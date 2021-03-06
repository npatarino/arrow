package arrow.effects

import arrow.Kind
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.effects.internal.CancelablePromise
import arrow.effects.internal.UncancelablePromise
import arrow.effects.typeclasses.Async
import arrow.effects.typeclasses.Concurrent

/**
 * When made, a [Promise] is empty. Until it is fulfilled, which can only happen once.
 *
 * A [Promise] guarantees (promises) [A] at some point in the future within the context of [F].
 * Note that since [F] is constrained to [Async] an error can also occur.
 */
interface Promise<F, A> {

  /**
   * Get the promised value.
   * Suspending the Fiber running the action until the result is available.
   *
   * ```kotlin:ank:playground
   * import arrow.effects.*
   * import arrow.effects.typeclasses.*
   * import arrow.effects.extensions.io.async.async
   * import arrow.effects.extensions.io.monad.flatMap
   *
   * fun main(args: Array<String>) {
   *   //sampleStart
   *   val promise = Promise.uncancelable<ForIO, Int>(IO.async())
   *
   *   promise.flatMap { p ->
   *     p.get()
   *   }  //Never ends since is uncancelable
   *
   *   promise.flatMap { p ->
   *     p.complete(1).flatMap {
   *       p.get()
   *     }
   *   }.unsafeRunSync() == IO.just(1).unsafeRunSync()
   *   //sampleEnd
   * }
   * ```
   */
  fun get(): Kind<F, A>

  /**
   * Try get the promised value, it returns [None] if promise is not fulfilled yet.
   * Returns [Some] of [A] if promise is fulfilled, [None] otherwise.
   *
   * ```kotlin:ank:playground
   * import arrow.core.*
   * import arrow.effects.*
   * import arrow.effects.typeclasses.*
   * import arrow.effects.extensions.io.async.async
   * import arrow.effects.extensions.io.monad.flatMap
   *
   * fun main(args: Array<String>) {
   *   //sampleStart
   *   val promise = Promise.uncancelable<ForIO, Int>(IO.async())
   *
   *   promise.flatMap { p ->
   *     p.tryGet()
   *   }.unsafeRunSync() == None
   *
   *   promise.flatMap { p ->
   *     p.complete(1).flatMap {
   *       p.tryGet()
   *     }
   *   }.unsafeRunSync() == Some(1)
   *   //sampleEnd
   * }
   * ```
   */
  fun tryGet(): Kind<F, Option<A>>

  /**
   * Completes, or fulfills, the promise with the specified value [A].
   * Results in an [Promise.AlreadyFulfilled] within [F] if the promise is already fulfilled.
   *
   * ```kotlin:ank:playground
   * import arrow.effects.*
   * import arrow.effects.extensions.io.async.async
   * import arrow.effects.extensions.io.monad.flatMap
   *
   * fun main(args: Array<String>) {
   *   //sampleStart
   *   val promise = Promise.uncancelable<ForIO, Int>(IO.async())
   *
   *   promise.flatMap { p ->
   *     p.complete(1).flatMap {
   *       p.get()
   *     }
   *   }.unsafeRunSync() == IO.just(1).unsafeRunSync()
   *
   *   promise.flatMap { p ->
   *     p.complete(1).flatMap {
   *       p.complete(2)
   *     }
   *   }.attempt().unsafeRunSync() ==
   *     IO.raiseError<Int>(Promise.AlreadyFulfilled).attempt().unsafeRunSync()
   *   //sampleEnd
   * }
   * ```
   */
  fun complete(a: A): Kind<F, Unit>

  /**
   * Try to complete, or fulfill, the promise with the specified value [A].
   * Returns `true` if the promise successfully completed, `false` otherwise.
   *
   * ```kotlin:ank:playground
   * import arrow.effects.*
   * import arrow.effects.extensions.io.async.async
   * import arrow.effects.extensions.io.monad.flatMap
   *
   * fun main(args: Array<String>) {
   *   //sampleStart
   *   val promise = Promise.uncancelable<ForIO, Int>(IO.async())
   *
   *   promise.flatMap { p ->
   *     p.tryComplete(1)
   *   }.unsafeRunSync() == true
   *
   *   promise.flatMap { p ->
   *     p.complete(1).flatMap {
   *       p.tryComplete(2)
   *     }
   *   }.unsafeRunSync() == false
   *   //sampleEnd
   * }
   * ```
   */
  fun tryComplete(a: A): Kind<F, Boolean>

  /**
   * Errors the promise with the specified [Throwable].
   * Results in an [Promise.AlreadyFulfilled] within [F] if the promise is already fulfilled.
   *
   * ```kotlin:ank:playground
   * import arrow.effects.*
   * import arrow.effects.extensions.io.async.async
   * import arrow.effects.extensions.io.monad.flatMap
   *
   * fun main(args: Array<String>) {
   *   //sampleStart
   *   val promise = Promise.uncancelable<ForIO, Int>(IO.async())
   *
   *   promise.flatMap { p ->
   *     p.error(RuntimeException("Boom"))
   *   }.attempt().unsafeRunSync() ==
   *     IO.raiseError<Int>(RuntimeException("Boom")).attempt().unsafeRunSync()
   *
   *   promise.flatMap { p ->
   *     p.complete(1).flatMap {
   *       p.error(RuntimeException("Boom"))
   *     }
   *   }.attempt().unsafeRunSync() ==
   *     IO.raiseError<Int>(Promise.AlreadyFulfilled).attempt().unsafeRunSync()
   *   //sampleEnd
   * }
   * ```
   */
  fun error(throwable: Throwable): Kind<F, Unit>

  /**
   * Tries to error the promise with the specified [Throwable].
   * Returns `true` if the promise already completed or errored, `false` otherwise.
   *
   * ```kotlin:ank:playground
   * import arrow.core.Right
   * import arrow.effects.*
   * import arrow.effects.extensions.io.async.async
   * import arrow.effects.extensions.io.monad.flatMap
   *
   * fun main(args: Array<String>) {
   *   //sampleStart
   *   val promise = Promise.uncancelable<ForIO, Int>(IO.async())
   *   val throwable = RuntimeException("Boom")
   *
   *   promise.flatMap { p ->
   *     p.tryError(throwable)
   *   }.attempt().unsafeRunSync() ==
   *     IO.raiseError<Int>(throwable).attempt().unsafeRunSync()
   *
   *   promise.flatMap { p ->
   *     p.complete(1).flatMap {
   *       p.tryError(RuntimeException("Boom"))
   *     }
   *   }.attempt().unsafeRunSync() == Right(false)
   *   //sampleEnd
   * }
   * ```
   */
  fun tryError(throwable: Throwable): Kind<F, Boolean>

  companion object {

    /**
     * Creates an empty `Promise` from on [Async] instance for [F].
     *
     * ```kotlin:ank:playground
     * import arrow.effects.*
     * import arrow.effects.extensions.io.concurrent.concurrent
     *
     * fun main(args: Array<String>) {
     *   //sampleStart
     *   val promise: IOOf<Promise<ForIO, Int>> = Promise(IO.concurrent())
     *   //sampleEnd
     * }
     * ```
     */
    operator fun <F, A> invoke(CF: Concurrent<F>): Kind<F, Promise<F, A>> =
      CF.delay { CancelablePromise<F, A>(CF) }

    /**
     * Creates an empty `Promise` from on [Concurrent] instance for [F].
     * This method is considered unsafe because it is not referentially transparent -- it allocates mutable state.
     *
     * ```kotlin:ank:playground
     * import arrow.effects.*
     * import arrow.effects.extensions.io.concurrent.concurrent
     *
     * fun main(args: Array<String>) {
     *   //sampleStart
     *   val unsafePromise: Promise<ForIO, Int> = Promise.unsafeCancelable(IO.concurrent())
     *   //sampleEnd
     * }
     * ```
     */
    fun <F, A> unsafeCancelable(CF: Concurrent<F>): Promise<F, A> = CancelablePromise(CF)

    /**
     * Creates an empty `Promise` from on [Async] instance for [F].
     * Does not support cancellation of [get] operation.
     *
     * ```kotlin:ank:playground
     * import arrow.effects.*
     * import arrow.effects.extensions.io.async.async
     *
     * fun main(args: Array<String>) {
     *   //sampleStart
     *   val promise: IOOf<Promise<ForIO, Int>> = Promise.uncancelable(IO.async())
     *   //sampleEnd
     * }
     * ```
     */
    fun <F, A> uncancelable(AS: Async<F>): Kind<F, Promise<F, A>> =
      AS.delay { UncancelablePromise<F, A>(AS) }

    /**
     * Creates an empty `Promise` from on [Async] instance for [F].
     * Does not support cancellation of [get] operation.
     * This method is considered unsafe because it is not referentially transparent -- it allocates mutable state.
     *
     * ```kotlin:ank:playground
     * import arrow.effects.*
     * import arrow.effects.extensions.io.async.async
     *
     * fun main(args: Array<String>) {
     *   //sampleStart
     *   val unsafePromise: Promise<ForIO, Int> = Promise.unsafeUncancelable(IO.async())
     *   //sampleEnd
     * }
     * ```
     */
    fun <F, A> unsafeUncancelable(AS: Async<F>): Promise<F, A> = UncancelablePromise(AS)
  }

  object AlreadyFulfilled : Throwable(message = "Promise was already fulfilled")
}
