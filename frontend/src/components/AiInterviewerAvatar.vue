<template>
  <div class="interviewer-avatar">
    <div class="avatar-container">
      <div class="avatar-glow" :style="glowStyle"></div>
      <div class="avatar-image">
        <i class="fas fa-user-tie"></i>
      </div>
      <div class="avatar-status" :class="{ speaking }">
        <span class="pulse"></span>
      </div>
      <div class="voice-ring" :style="ringStyle"></div>
    </div>
  </div>
</template>

<script>
import { computed } from 'vue'

export default {
  name: 'AiInterviewerAvatar',
  props: {
    speaking: { type: Boolean, default: false },
    volumeLevel: { type: Number, default: 0 },
    roleName: { type: String, default: '技术' }
  },
  setup(props) {
    const ringScale = computed(() => {
      if (!props.speaking) return 1
      return 1 + Math.min(0.8, props.volumeLevel / 120)
    })
    const ringOpacity = computed(() => (props.speaking ? 0.2 + props.volumeLevel / 180 : 0.12))
    const glowStyle = computed(() => ({
      transform: `scale(${ringScale.value})`,
      opacity: ringOpacity.value
    }))
    const ringStyle = computed(() => ({
      transform: `scale(${ringScale.value})`,
      opacity: ringOpacity.value
    }))
    return { glowStyle, ringStyle }
  }
}
</script>

<style scoped>
.interviewer-avatar { text-align: center; }
.avatar-container {
  position: relative;
  width: 120px;
  height: 120px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-glow, .voice-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(79, 172, 254, 0.42), rgba(79, 172, 254, 0.06));
  transition: transform 120ms ease, opacity 120ms ease;
}
.voice-ring { border: 1px solid rgba(79, 172, 254, 0.45); }
.avatar-image {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 32px;
  z-index: 2;
}
.avatar-status {
  position: absolute;
  bottom: 4px;
  right: 10px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #6b7280;
  z-index: 3;
}
.avatar-status.speaking { background: #10b981; }
</style>

