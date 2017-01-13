/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.coroutines

import kotlin.test.*
import org.junit.Test

class CoroutineContextTest {
    data class CtxA(val i: Int) : AbstractCoroutineContextElement() {
        companion object : CoroutineContextKey<CtxA>
        override val contextKey get() = CtxA
    }

    data class CtxB(val i: Int) : AbstractCoroutineContextElement() {
        companion object : CoroutineContextKey<CtxB>
        override val contextKey get() = CtxB
    }

    data class CtxC(val i: Int) : AbstractCoroutineContextElement() {
        companion object : CoroutineContextKey<CtxC>
        override val contextKey get() = CtxC
    }

    object Disp1 : AbstractCoroutineContextElement(), ContinuationInterceptor {
        override fun <T> interceptContinuation(continuation: CoroutineContinuation<T>): CoroutineContinuation<T> = continuation
        override val contextKey: CoroutineContextKey<*> = ContinuationInterceptor
        override fun toString(): String = "Disp1"
    }

    object Disp2 : AbstractCoroutineContextElement(), ContinuationInterceptor {
        override fun <T> interceptContinuation(continuation: CoroutineContinuation<T>): CoroutineContinuation<T> = continuation
        override val contextKey: CoroutineContextKey<*> = ContinuationInterceptor
        override fun toString(): String = "Disp2"
    }

    @Test
    fun testGetPlusFold() {
        var ctx: CoroutineContext = EmptyCoroutineContext
        assertContents(ctx)
        assertEquals("EmptyCoroutineContext", ctx.toString())

        ctx += CtxA(1)
        assertContents(ctx, CtxA(1))
        assertEquals("CtxA(i=1)", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(null, ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx += CtxB(2)
        assertContents(ctx, CtxA(1), CtxB(2))
        assertEquals("[CtxA(i=1), CtxB(i=2)]", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx += CtxC(3)
        assertContents(ctx, CtxA(1), CtxB(2), CtxC(3))
        assertEquals("[CtxA(i=1), CtxB(i=2), CtxC(i=3)]", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx += CtxB(4)
        assertContents(ctx, CtxA(1), CtxC(3), CtxB(4))
        assertEquals("[CtxA(i=1), CtxC(i=3), CtxB(i=4)]", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(4), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx += CtxA(5)
        assertContents(ctx, CtxC(3), CtxB(4), CtxA(5))
        assertEquals("[CtxC(i=3), CtxB(i=4), CtxA(i=5)]", ctx.toString())
        assertEquals(CtxA(5), ctx[CtxA])
        assertEquals(CtxB(4), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])
    }

    @Test
    fun testMinusKey() {
        var ctx: CoroutineContext = CtxA(1) + CtxB(2) + CtxC(3)
        assertContents(ctx, CtxA(1), CtxB(2), CtxC(3))
        assertEquals("[CtxA(i=1), CtxB(i=2), CtxC(i=3)]", ctx.toString())

        ctx = ctx.minusKey(CtxA)
        assertContents(ctx, CtxB(2), CtxC(3))
        assertEquals("[CtxB(i=2), CtxC(i=3)]", ctx.toString())
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(CtxC(3), ctx[CtxC])

        ctx = ctx.minusKey(CtxC)
        assertContents(ctx, CtxB(2))
        assertEquals("CtxB(i=2)", ctx.toString())
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx = ctx.minusKey(CtxC)
        assertContents(ctx, CtxB(2))
        assertEquals("CtxB(i=2)", ctx.toString())
        assertEquals(null, ctx[CtxA])
        assertEquals(CtxB(2), ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        ctx = ctx.minusKey(CtxB)
        assertContents(ctx)
        assertEquals("EmptyCoroutineContext", ctx.toString())
        assertEquals(null, ctx[CtxA])
        assertEquals(null, ctx[CtxB])
        assertEquals(null, ctx[CtxC])

        assertEquals(EmptyCoroutineContext, ctx)
    }

    @Test
    fun testPlusCombined() {
        val ctx1 = CtxA(1) + CtxB(2)
        val ctx2 = CtxB(3) + CtxC(4)
        val ctx = ctx1 + ctx2
        assertContents(ctx, CtxA(1), CtxB(3), CtxC(4))
        assertEquals("[CtxA(i=1), CtxB(i=3), CtxC(i=4)]", ctx.toString())
        assertEquals(CtxA(1), ctx[CtxA])
        assertEquals(CtxB(3), ctx[CtxB])
        assertEquals(CtxC(4), ctx[CtxC])
    }

    @Test
    fun testLastDispatcher() {
        var ctx: CoroutineContext = EmptyCoroutineContext
        assertContents(ctx)
        ctx += CtxA(1)
        assertContents(ctx, CtxA(1))
        ctx += Disp1
        assertContents(ctx, CtxA(1), Disp1)
        ctx += CtxA(2)
        assertContents(ctx, CtxA(2), Disp1)
        ctx += CtxB(3)
        assertContents(ctx, CtxA(2), CtxB(3), Disp1)
        ctx += Disp2
        assertContents(ctx, CtxA(2), CtxB(3), Disp2)
        ctx += (CtxB(4) + CtxC(5))
        assertContents(ctx, CtxA(2), CtxB(4), CtxC(5), Disp2)
    }

    @Test
    fun testEquals() {
        val ctx1 = CtxA(1) + CtxB(2) + CtxC(3)
        val ctx2 = CtxB(2) + CtxC(3) + CtxA(1) // same
        val ctx3 = CtxC(3) + CtxA(1) + CtxB(2) // same
        val ctx4 = CtxA(1) + CtxB(2) + CtxC(4) // different
        assertEquals(ctx1, ctx2)
        assertEquals(ctx1, ctx3)
        assertEquals(ctx2, ctx3)
        assertNotEquals(ctx1, ctx4)
        assertNotEquals(ctx2, ctx4)
        assertNotEquals(ctx3, ctx4)
    }

    private fun  assertContents(ctx: CoroutineContext, vararg elements: CoroutineContextElement) {
        val set = ctx.fold(setOf<CoroutineContext>()) { a, b -> a + b }
        assertEquals(listOf(*elements), set.toList())
        for (elem in elements)
            assertTrue(ctx[elem.contextKey] == elem)
    }
}