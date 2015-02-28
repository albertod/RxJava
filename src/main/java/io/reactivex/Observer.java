package io.reactivex;

public interface Observer<T> {

    public void onNext(T t);
    
    public void onError(Throwable t);
    
    public void onComplete();
}
