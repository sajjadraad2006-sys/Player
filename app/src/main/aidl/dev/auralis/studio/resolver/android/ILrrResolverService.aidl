package dev.auralis.studio.resolver.android;

import dev.auralis.studio.resolver.android.ILrrResolverCallback;

/**
 * AIDL service contract for the isolated LRR resolver process (:resolver).
 * Uses asynchronous callbacks to avoid blocking Binder worker threads.
 */
interface ILrrResolverService {
    void inspect(String requestJson, ILrrResolverCallback callback);
    void resolve(String requestJson, ILrrResolverCallback callback);
    void cancelRequest(String requestId);
    String getHealthJson();
}
