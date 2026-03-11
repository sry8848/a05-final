<template>
  <section id="page-home" class="page-section active">
    <header class="welcome-header">
      <div class="welcome-text">
        <h2>欢迎回来，{{ user.name }}</h2>
        <p>准备好开始你的AI模拟面试之旅了吗？</p>
      </div>
      <div class="quick-actions">
        <button class="btn btn-primary glass-btn" @click="$emit('navigate', 'interview')">
          <i class="fas fa-play"></i>
          开始面试
        </button>
      </div>
    </header>
    
    <div class="stats-grid">
      <div class="stat-card glass-card">
        <div class="stat-icon blue">
          <i class="fas fa-calendar-check"></i>
        </div>
        <div class="stat-content">
          <span class="stat-number">{{ user.totalInterviews }}</span>
          <span class="stat-label">总面试次数</span>
        </div>
      </div>
      
      <div class="stat-card glass-card">
        <div class="stat-icon green">
          <i class="fas fa-star"></i>
        </div>
        <div class="stat-content">
          <span class="stat-number">{{ user.avgScore }}</span>
          <span class="stat-label">平均得分</span>
        </div>
      </div>
      
      <div class="stat-card glass-card">
        <div class="stat-icon purple">
          <i class="fas fa-clock"></i>
        </div>
        <div class="stat-content">
          <span class="stat-number">{{ user.totalHours }}</span>
          <span class="stat-label">学习时长(h)</span>
        </div>
      </div>
      
      <div class="stat-card glass-card">
        <div class="stat-icon orange">
          <i class="fas fa-trophy"></i>
        </div>
        <div class="stat-content">
          <span class="stat-number">{{ user.ranking }}</span>
          <span class="stat-label">全国排名</span>
        </div>
      </div>
    </div>
    
    <div class="section-container">
      <h3 class="section-title">
        <i class="fas fa-fire"></i>
        推荐面试岗位
      </h3>
      <div class="job-cards-grid">
        <div class="job-card glass-card" @click="$emit('selectJob', 'frontend')">
          <div class="job-icon">
            <i class="fas fa-code"></i>
          </div>
          <h4>前端开发工程师</h4>
          <p>HTML/CSS/JavaScript/Vue/React</p>
          <div class="job-meta">
            <span><i class="fas fa-users"></i> 1.2k人参与</span>
            <span><i class="fas fa-signal"></i> 中等难度</span>
          </div>
        </div>
        
        <div class="job-card glass-card" @click="$emit('selectJob', 'backend')">
          <div class="job-icon">
            <i class="fas fa-server"></i>
          </div>
          <h4>后端开发工程师</h4>
          <p>Java/Python/Go/数据库</p>
          <div class="job-meta">
            <span><i class="fas fa-users"></i> 980人参与</span>
            <span><i class="fas fa-signal"></i> 较高难度</span>
          </div>
        </div>
        
        <div class="job-card glass-card" @click="$emit('selectJob', 'fullstack')">
          <div class="job-icon">
            <i class="fas fa-layer-group"></i>
          </div>
          <h4>全栈开发工程师</h4>
          <p>前后端技术全掌握</p>
          <div class="job-meta">
            <span><i class="fas fa-users"></i> 656人参与</span>
            <span><i class="fas fa-signal"></i> 高难度</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: 'HomePage',
  props: {
    user: {
      type: Object,
      default: () => ({
        name: '面试者',
        totalInterviews: 12,
        avgScore: 85,
        totalHours: 36,
        ranking: 'Top 10%'
      })
    }
  },
  emits: ['navigate', 'selectJob']
}
</script>
