package dev.auralis.studio.audio.stems

enum class StemProcessingStage { COPYING_SOURCE, DECODING, SEPARATING, SAVING }
enum class StemErrorCode { DOWNLOAD_FAILED, MODEL_INTEGRITY, MODEL_UNAVAILABLE, NOT_ENOUGH_STORAGE, SEPARATION_FAILED }

sealed interface StemUiState {
    data object CheckingModel : StemUiState
    data class ModelRequired(val bytes: Long) : StemUiState
    data class DownloadingModel(val progress: Int) : StemUiState
    data object ReadyForTrack : StemUiState
    data class Processing(
        val stage: StemProcessingStage,
        val progress: Int?,
        val sourceIdentity: String,
    ) : StemUiState
    data class Complete(val result: StemSeparationResult, val sourceIdentity: String) : StemUiState
    data class Error(val code: StemErrorCode, val modelInstalled: Boolean) : StemUiState
}
