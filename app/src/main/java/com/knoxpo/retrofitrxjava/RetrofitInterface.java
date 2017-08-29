package com.knoxpo.retrofitrxjava;

import io.reactivex.Single;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Created by Tejas Sherdiwala on 25/08/17.
 */

public interface RetrofitInterface {
    @GET("post/{id}")
    Single<Post> getPost(@Path("id") int id);
}
