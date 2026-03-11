<template>
  <section id="page-analysis" class="page-section active">
    <h3 class="section-title">
      <i class="fas fa-chart-line"></i>
      数据分析
    </h3>
    
    <div class="analysis-grid">
      <div class="analysis-card glass-card">
        <h4>能力雷达图</h4>
        <div class="chart-container">
          <div class="radar-chart">
            <div class="radar-area" :style="radarStyle">
              <div class="radar-point p1"></div>
              <div class="radar-point p2"></div>
              <div class="radar-point p3"></div>
              <div class="radar-point p4"></div>
              <div class="radar-point p5"></div>
              <div class="radar-point p6"></div>
            </div>
            <div class="radar-labels">
              <span class="label-1">JavaScript</span>
              <span class="label-2">CSS</span>
              <span class="label-3">Vue</span>
              <span class="label-4">React</span>
              <span class="label-5">算法</span>
              <span class="label-6">项目经验</span>
            </div>
          </div>
        </div>
      </div>
      
      <div class="analysis-card glass-card">
        <h4>得分趋势</h4>
        <div class="trend-chart">
          <div class="trend-bars">
            <div 
              v-for="(bar, index) in trendBars" 
              :key="index"
              class="trend-bar" 
              :style="{ '--height': bar.height + '%' }"
              :data-value="bar.value"
            ></div>
          </div>
          <div class="trend-labels">
            <span v-for="(label, index) in trendLabels" :key="index">{{ label }}</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { ref, computed } from 'vue'

export default {
  name: 'AnalysisPage',
  setup() {
    const skills = ref([80, 70, 85, 65, 75, 70])
    
    const trendBars = ref([
      { height: 60, value: 60 },
      { height: 75, value: 75 },
      { height: 70, value: 70 },
      { height: 85, value: 85 },
      { height: 80, value: 80 },
      { height: 90, value: 90 }
    ])
    
    const trendLabels = ref(['周一', '周二', '周三', '周四', '周五', '周六'])

    const radarStyle = computed(() => {
      return {
        '--skill1': skills.value[0] + '%',
        '--skill2': skills.value[1] + '%',
        '--skill3': skills.value[2] + '%',
        '--skill4': skills.value[3] + '%',
        '--skill5': skills.value[4] + '%',
        '--skill6': skills.value[5] + '%'
      }
    })

    return {
      skills,
      trendBars,
      trendLabels,
      radarStyle
    }
  }
}
</script>

<style scoped>
.radar-chart {
  position: relative;
  width: 250px;
  height: 250px;
  margin: 0 auto;
}

.radar-area {
  position: absolute;
  width: 100%;
  height: 100%;
  clip-path: polygon(
    50% 0%, 
    93.3% 25%, 
    93.3% 75%, 
    50% 100%, 
    6.7% 75%, 
    6.7% 25%
  );
  background: rgba(59, 89, 152, 0.15);
  border: 2px solid var(--primary-color);
}

.radar-point {
  position: absolute;
  width: 12px;
  height: 12px;
  background: var(--primary-color);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 10px var(--primary-color);
}

.radar-point.p1 { top: calc(100% - var(--skill1)); left: 50%; }
.radar-point.p2 { top: calc(75% - (var(--skill2) - 50%) * 0.5); right: calc(6.7% - (var(--skill2) - 50%) * 0.433); }
.radar-point.p3 { bottom: calc(25% - (var(--skill3) - 50%) * 0.5); right: calc(6.7% - (var(--skill3) - 50%) * 0.433); }
.radar-point.p4 { bottom: calc(100% - var(--skill4)); left: 50%; }
.radar-point.p5 { bottom: calc(25% - (var(--skill5) - 50%) * 0.5); left: calc(6.7% - (var(--skill5) - 50%) * 0.433); }
.radar-point.p6 { top: calc(75% - (var(--skill6) - 50%) * 0.5); left: calc(6.7% - (var(--skill6) - 50%) * 0.433); }

.radar-labels {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
}

.radar-labels span {
  position: absolute;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.label-1 { top: -25px; left: 50%; transform: translateX(-50%); }
.label-2 { top: 25%; right: -60px; }
.label-3 { bottom: 25%; right: -60px; }
.label-4 { bottom: -25px; left: 50%; transform: translateX(-50%); }
.label-5 { bottom: 25%; left: -60px; }
.label-6 { top: 25%; left: -60px; }

.trend-chart {
  height: 200px;
  display: flex;
  flex-direction: column;
}

.trend-bars {
  flex: 1;
  display: flex;
  align-items: flex-end;
  gap: 16px;
  padding: 0 10px;
}

.trend-bar {
  flex: 1;
  height: var(--height);
  background: linear-gradient(to top, var(--primary-color), var(--primary-light));
  border-radius: 4px 4px 0 0;
  position: relative;
  transition: height 0.3s ease;
}

.trend-bar::after {
  content: attr(data-value);
  position: absolute;
  top: -20px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  color: var(--text-secondary);
}

.trend-labels {
  display: flex;
  gap: 16px;
  padding: 10px 10px 0;
}

.trend-labels span {
  flex: 1;
  text-align: center;
  font-size: 12px;
  color: var(--text-secondary);
}
</style>
