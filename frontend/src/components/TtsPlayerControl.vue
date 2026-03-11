<template>
  <div class="tts-panel">
    <div class="tts-modes">
      <button
        v-for="item in modes"
        :key="item.value"
        class="tts-mode-btn"
        :class="{ active: modelValue === item.value }"
        @click="$emit('update:modelValue', item.value)"
      >
        {{ item.label }}
      </button>
    </div>
    <div class="tts-actions">
      <button class="tts-action-btn" :disabled="modelValue === 'mute'" @click="$emit('manualPlay')">播放</button>
      <button class="tts-action-btn" @click="$emit('skip')">跳过</button>
      <button class="tts-action-btn" @click="$emit('interrupt')">打断</button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TtsPlayerControl',
  props: {
    modelValue: { type: String, default: 'auto' }
  },
  emits: ['update:modelValue', 'manualPlay', 'skip', 'interrupt'],
  setup() {
    const modes = [
      { value: 'auto', label: '自动播报' },
      { value: 'manual', label: '手动播放' },
      { value: 'mute', label: '静音模式' }
    ]
    return { modes }
  }
}
</script>

<style scoped>
.tts-panel { margin-top: 12px; display: flex; flex-direction: column; gap: 10px; }
.tts-modes, .tts-actions { display: flex; gap: 8px; }
.tts-mode-btn, .tts-action-btn {
  border: 1px solid var(--glass-border);
  background: var(--glass-bg);
  color: var(--text-primary);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}
.tts-mode-btn.active { border-color: #4facfe; color: #4facfe; }
.tts-action-btn:disabled { opacity: 0.5; cursor: not-allowed; }
</style>

