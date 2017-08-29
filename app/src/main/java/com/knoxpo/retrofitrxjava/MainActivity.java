package com.knoxpo.retrofitrxjava;


import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import org.reactivestreams.Publisher;

import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;

/**
 * Created by Tejas Sherdiwala on 25/08/17.
 */

public class MainActivity extends Activity {

    private static final int UNCHECKED_ERROR_TYPE_CODE = -100;

    private static final String
            TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RetrofitInterface retrofitInterface
                = RetrofitHelper.getRetrofit().create(RetrofitInterface.class);

        Single<Post> single = retrofitInterface.getPost(1);

        single.retryWhen(
                new Function<Flowable<Throwable>, Publisher<?>>() {
                    @Override
                    public Publisher<?> apply(@NonNull Flowable<Throwable> throwableFlowable) throws Exception {
                        return exponentialBackoffForExceptions(throwableFlowable, 2, 3, TimeUnit.SECONDS, HttpException.class);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableSingleObserver<Post>() {
                    @Override
                    public void onSuccess(@NonNull Post post) {
                        Log.d(TAG, "Success");
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Log.e(TAG, "Error occurred");
                    }
                });

    }

    public static Flowable<?> exponentialBackoffForExceptions(Flowable<Throwable> errors, final long delay, final int retries, final TimeUnit timeUnit, final Class<? extends Throwable>... errorTypes) {
        if (delay <= 0) {
            throw new IllegalArgumentException("delay must be greater than 0");
        }

        if (retries <= 0) {
            throw new IllegalArgumentException("retries must be greater than 0");
        }

        Flowable<Pair<Throwable, Integer>> flowable = errors.zipWith(Flowable.range(1, retries + 1), new BiFunction<Throwable, Integer, Pair<Throwable, Integer>>() {
            @Override
            public Pair<Throwable, Integer> apply(@NonNull Throwable error, @NonNull Integer integer) throws Exception {

                Log.d(TAG, "Retry count: "+integer);

                Pair<Throwable, Integer> errorPair = null;
                if (integer == retries + 1) {
                    errorPair = new Pair<>(error, UNCHECKED_ERROR_TYPE_CODE);
                }

                if (errorTypes != null && errorPair == null) {
                    for (Class<? extends Throwable> clazz : errorTypes) {
                        if (clazz.isInstance(error)) {
                            // Mark as error type found
                            errorPair = new Pair<>(error, integer);
                            break;
                        }
                    }
                }

                if (errorPair == null) {
                    errorPair = new Pair<>(error, UNCHECKED_ERROR_TYPE_CODE);
                }

                return errorPair;
            }
        });

        Flowable flatFlowable = flowable.flatMap(new Function<Pair<Throwable, Integer>, Publisher<?>>() {
            @Override
            public Publisher<?> apply(@NonNull Pair<Throwable, Integer> errorRetryCountTuple) throws Exception {
                int retryAttempt = errorRetryCountTuple.second;

                // If not a known error type, pass the error through.
                if (retryAttempt == UNCHECKED_ERROR_TYPE_CODE) {
                    return Flowable.error(errorRetryCountTuple.first);
                }

                long d = (long) Math.pow(delay, retryAttempt);

                Log.d(TAG, "Retry after: "+d);

                // Else, exponential backoff for the passed in error types.
                return Flowable.timer(d, timeUnit);
            }
        });

        return flatFlowable;
    }
}
