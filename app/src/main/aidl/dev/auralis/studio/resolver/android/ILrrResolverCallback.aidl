package dev.auralis.studio.resolver.android;

/**
 * Asynchronous callback interface for LRR Binder IPC.
 */
interface ILrrResolverCallback {
    void onSuccess(String requestId, String resultJson);
    void onFailure(String requestId, String errorJson);
}
