export function formatLoadingProgress(progress) {
  const numericProgress = Number(progress)
  if (!Number.isFinite(numericProgress)) return 0

  return Math.max(0, Math.min(100, Math.round(numericProgress)))
}
