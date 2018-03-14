/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.fir.visitors.generator

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.local.CoreLocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.Printer
import java.io.File


const val FIR_ELEMENT_CLASS_NAME = "FirElement"
const val VISITOR_PACKAGE = "org.jetbrains.kotlin.fir.visitors"
const val SIMPLE_VISITOR_NAME = "FirVisitor"
const val UNIT_VISITOR_NAME = "FirVisitorVoid"

fun main(args: Array<String>) {

    val rootPath = File(args[0])
    val output = File(args[1])

    val packageDirectory = output.resolve(VISITOR_PACKAGE.replace('.', '/'))
    packageDirectory.mkdirs()

    withPsiSetup {
        val psiManager = PsiManager.getInstance(project)
        val vfm = VirtualFileManager.getInstance()


        val dataCollector = DataCollector()

        rootPath.walkTopDown()
            .filter {
                it.extension == "kt"
            }.map {
                (vfm.getFileSystem(StandardFileSystems.FILE_PROTOCOL) as CoreLocalFileSystem).findFileByIoFile(it)
            }.flatMap {
                SingleRootFileViewProvider(psiManager, it!!).allFiles.asSequence()
            }
            .filterIsInstance<KtFile>()
            .forEach(dataCollector::readFile)


        val data = dataCollector.computeResult()


        packageDirectory
            .resolve("${SIMPLE_VISITOR_NAME}Generated.kt")
            .writeText(
                SimpleVisitorGenerator(data).generate()
            )

        packageDirectory
            .resolve("${UNIT_VISITOR_NAME}Generated.kt")
            .writeText(
                UnitVisitorGenerator(data).generate()
            )
    }
}


val String.classNameWithoutFir get() = this.removePrefix("Fir")


fun DataCollector.ReferencesData.walkHierarchyTopDown(from: String, l: (p: String, e: String) -> Unit) {
    val referents = back[from] ?: return
    for (referent in referents) {
        l(from, referent)
        walkHierarchyTopDown(referent, l)
    }
}

abstract class AbstractVisitorGenerator(val referencesData: DataCollector.ReferencesData) {
    fun Printer.generateFunction(
        name: String,
        parameters: Map<String, String>,
        returnType: String,
        override: Boolean = false,
        final: Boolean = false,
        body: (Printer.() -> Unit)?
    ) {

        if (body == null) {
            print("abstract ")
        } else {
            printIndent()
            if (!final) {
                printWithNoIndent("open ")
            }
            if (override) {
                if (final) {
                    printWithNoIndent("final ")
                }
                printWithNoIndent("override ")
            }
        }
        printWithNoIndent("fun ", name, "(")
        parameters
            .flatMap { (a, b) ->
                listOf(a, ": ", b, ", ")
            }.dropLast(1)
            .forEach {
                printWithNoIndent(it)
            }
        printWithNoIndent(")")
        if (returnType != "Unit") {
            printWithNoIndent(": ", returnType)
        }
        if (body != null) {
            printlnWithNoIndent(" {")
            indented {
                body()
            }
            println("}")
        } else {
            printlnWithNoIndent()
        }
        println()
    }

    protected inline fun Printer.indented(l: () -> Unit) {
        pushIndent()
        l()
        popIndent()
    }

    protected fun Printer.generateCall(name: String, args: List<String>) {
        printWithNoIndent(name, "(")
        separatedOneLine(args, ", ")
        printWithNoIndent(")")
    }

    protected fun Printer.separatedOneLine(iterable: Iterable<Any>, separator: Any) {
        var first = true
        for (element in iterable) {
            if (!first) {
                printWithNoIndent(separator)
            } else {
                first = false
            }
            printWithNoIndent(element)
        }
    }

    protected fun Printer.generateDefaultImports() {
        referencesData.usedPackages.forEach {
            println("import ", it.asString(), ".*")
        }
        println()
        println()
    }

    val String.safeName
        get() = when (this) {
            "class" -> "klass"
            else -> this
        }

    fun generate(): String {
        val builder = StringBuilder()
        val printer = Printer(builder, "    ")
        printer.apply {
            println("package $VISITOR_PACKAGE")
            println()
            printer.generateContent()
        }
        return builder.toString()
    }

    abstract fun Printer.generateContent()

}


class SimpleVisitorGenerator(referencesData: DataCollector.ReferencesData) : AbstractVisitorGenerator(referencesData) {
    override fun Printer.generateContent() {
        generateDefaultImports()
        println("abstract class $SIMPLE_VISITOR_NAME<R, D> {")
        indented {
            generateFunction(
                "visitElement",
                parameters = mapOf(
                    "element" to FIR_ELEMENT_CLASS_NAME,
                    "data" to "D"
                ),
                returnType = "R",
                body = null
            )
            referencesData.walkHierarchyTopDown(from = FIR_ELEMENT_CLASS_NAME) { parent, element ->
                generateVisit(element, parent)
            }
        }
        println("}")
    }

    private fun Printer.generateVisit(className: String, parent: String) {
        val shortcutName = className.classNameWithoutFir
        val parameterName = shortcutName.decapitalize().safeName
        generateFunction(
            name = "visit$shortcutName",
            parameters = mapOf(
                parameterName to className,
                "data" to "D"
            ),
            returnType = "R"
        ) {
            print("return ")
            generateCall("visit${parent.classNameWithoutFir}", listOf(parameterName, "data"))
            println()
        }
    }
}


class UnitVisitorGenerator(referencesData: DataCollector.ReferencesData) : AbstractVisitorGenerator(referencesData) {
    override fun Printer.generateContent() {
        generateDefaultImports()
        println("abstract class $UNIT_VISITOR_NAME : $SIMPLE_VISITOR_NAME<Unit, Nothing?>() {")
        indented {
            generateFunction(
                "visitElement",
                parameters = mapOf(
                    "element" to FIR_ELEMENT_CLASS_NAME
                ),
                returnType = "Unit",
                body = null
            )

            referencesData.walkHierarchyTopDown(FIR_ELEMENT_CLASS_NAME) { parent, klass ->
                generateVisit(klass, parent)
            }

            referencesData.back.keys.forEach {
                generateTrampolineVisit(it)
            }

        }
        println("}")
    }


    private fun Printer.generateVisit(className: String, parent: String) {
        val shortcutName = className.classNameWithoutFir
        val parameterName = shortcutName.decapitalize().safeName
        generateFunction(
            name = "visit$shortcutName",
            parameters = mapOf(
                parameterName to className
            ),
            returnType = "Unit"
        ) {
            printIndent()
            generateCall("visit${parent.classNameWithoutFir}", listOf(parameterName, "null"))
            println()
        }
    }

    private fun Printer.generateTrampolineVisit(className: String) {
        val shortcutName = className.classNameWithoutFir
        val parameterName = shortcutName.decapitalize().safeName
        generateFunction(
            name = "visit$shortcutName",
            parameters = mapOf(
                parameterName to className,
                "data" to "Nothing?"
            ),
            returnType = "Unit",
            override = true,
            final = true
        ) {
            printIndent()
            generateCall("visit$shortcutName", listOf(parameterName))
            println()
        }
    }
}