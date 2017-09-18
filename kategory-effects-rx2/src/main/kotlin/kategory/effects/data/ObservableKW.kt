package kategory

import io.reactivex.Observable
import io.reactivex.ObservableEmitter

fun <A> Observable<A>.k(): ObservableKW<A> = ObservableKW(this)

fun <A> ObservableKWKind<A>.value(): Observable<A> =
        this.ev().observable

@higherkind
@deriving(Functor::class, Applicative::class, AsyncContext::class)
data class ObservableKW<A>(val observable: Observable<A>) : ObservableKWKind<A> {
    fun <B> map(f: (A) -> B): ObservableKW<B> =
            observable.map(f).k()

    fun <B> ap(fa: ObservableKWKind<(A) -> B>): ObservableKW<B> =
            flatMap { a -> fa.ev().map { ff -> ff(a) } }

    fun <B> flatMap(f: (A) -> ObservableKW<B>): ObservableKW<B> =
            observable.flatMap { f(it).observable }.k()

    fun <B> concatMap(f: (A) -> ObservableKW<B>): ObservableKW<B> =
            observable.concatMap { f(it).observable }.k()

    fun <B> switchMap(f: (A) -> ObservableKW<B>): ObservableKW<B> =
            observable.switchMap { f(it).observable }.k()

    companion object {
        fun <A> pure(a: A): ObservableKW<A> =
                Observable.just(a).k()

        fun <A> raiseError(t: Throwable): ObservableKW<A> =
                Observable.error<A>(t).k()

        fun <A> runAsync(fa: Proc<A>): ObservableKW<A> =
                Observable.create { emitter: ObservableEmitter<A> ->
                    fa { either: Either<Throwable, A> ->
                        either.fold({
                            emitter.onError(it)
                        }, {
                            emitter.onNext(it)
                            emitter.onComplete()
                        })

                    }
                }.k()

        fun monadFlat(): ObservableKWMonadInstance = ObservableKWMonadInstanceImplicits.instance()

        fun monadConcat(): ObservableKWMonadInstance = object : ObservableKWMonadInstance {
            override fun <A, B> flatMap(fa: ObservableKWKind<A>, f: (A) -> ObservableKWKind<B>): ObservableKW<B> =
                    fa.ev().concatMap { f(it).ev() }

            override fun <A, B> tailRecM(a: A, f: (A) -> ObservableKWKind<Either<A, B>>): ObservableKW<B> =
                    f(a).ev().concatMap {
                        it.fold({ tailRecM(a, f).ev() }, { pure(it).ev() })
                    }
        }

        fun monadSwitch(): ObservableKWMonadInstance = object : ObservableKWMonadErrorInstance {
            override fun <A, B> flatMap(fa: ObservableKWKind<A>, f: (A) -> ObservableKWKind<B>): ObservableKW<B> =
                    fa.ev().switchMap { f(it).ev() }

            override fun <A, B> tailRecM(a: A, f: (A) -> ObservableKWKind<Either<A, B>>): ObservableKW<B> =
                    f(a).ev().switchMap {
                        it.fold({ tailRecM(a, f).ev() }, { pure(it).ev() })
                    }
        }

        fun monadErrorFlat(): ObservableKWMonadErrorInstance = ObservableKWMonadErrorInstanceImplicits.instance()

        fun monadErrorConcat(): ObservableKWMonadErrorInstance = object : ObservableKWMonadErrorInstance {
            override fun <A, B> flatMap(fa: ObservableKWKind<A>, f: (A) -> ObservableKWKind<B>): ObservableKW<B> =
                    fa.ev().concatMap { f(it).ev() }

            override fun <A, B> tailRecM(a: A, f: (A) -> ObservableKWKind<Either<A, B>>): ObservableKW<B> =
                    f(a).ev().concatMap {
                        it.fold({ tailRecM(a, f).ev() }, { pure(it).ev() })
                    }
        }

        fun monadErrorSwitch(): ObservableKWMonadErrorInstance = object : ObservableKWMonadErrorInstance {
            override fun <A, B> flatMap(fa: ObservableKWKind<A>, f: (A) -> ObservableKWKind<B>): ObservableKW<B> =
                    fa.ev().switchMap { f(it).ev() }

            override fun <A, B> tailRecM(a: A, f: (A) -> ObservableKWKind<Either<A, B>>): ObservableKW<B> =
                    f(a).ev().switchMap {
                        it.fold({ tailRecM(a, f).ev() }, { pure(it).ev() })
                    }
        }
    }
}

fun <A> ObservableKW<A>.handleErrorWith(function: (Throwable) -> ObservableKW<A>): ObservableKW<A> =
        this.observable.onErrorResumeNext { t: Throwable -> function(t).observable }.k()