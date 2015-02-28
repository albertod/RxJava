package io.reactivex;

public interface Disposable {

    public void dispose();
    
    public boolean isDisposed();
}
