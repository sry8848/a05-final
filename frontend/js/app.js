/**
 * ============================================================
 * AI模拟面试系统 - 主应用逻辑
 * 基于RuoYi框架设计理念
 * ============================================================
 */

/* ==================== 全局状态管理 ==================== */
const AppState = {
    currentPage: 'home',
    isDarkMode: false,
    user: {
        name: '面试者',
        level: 'Lv.1 初级工程师',
        totalInterviews: 12,
        avgScore: 85,
        totalHours: 36,
        ranking: 'Top 10%'
    }
};

/* ==================== DOM元素缓存 ==================== */
const DOM = {
    navItems: null,
    pageSections: null,
    darkModeToggle: null,
    sidebar: null
};

/* ==================== 初始化函数 ==================== */
function initApp() {
    cacheDOMElements();
    bindEvents();
    loadUserSettings();
    initHistoryList();
    console.log('AI面试系统初始化完成');
}

/* ==================== 缓存DOM元素 ==================== */
function cacheDOMElements() {
    DOM.navItems = document.querySelectorAll('.nav-item');
    DOM.pageSections = document.querySelectorAll('.page-section');
    DOM.darkModeToggle = document.getElementById('dark-mode-toggle');
    DOM.sidebar = document.querySelector('.sidebar');
}

/* ==================== 绑定事件监听 ==================== */
function bindEvents() {
    DOM.navItems.forEach(item => {
        item.addEventListener('click', handleNavClick);
    });
    
    if (DOM.darkModeToggle) {
        DOM.darkModeToggle.addEventListener('change', toggleDarkMode);
    }
    
    const questionSlider = document.getElementById('question-slider');
    if (questionSlider) {
        questionSlider.addEventListener('input', updateQuestionCount);
    }
    
    const diffBtns = document.querySelectorAll('.diff-btn');
    diffBtns.forEach(btn => {
        btn.addEventListener('click', handleDifficultySelect);
    });
    
    const modeOptions = document.querySelectorAll('.mode-option');
    modeOptions.forEach(option => {
        option.addEventListener('click', handleModeSelect);
    });
    
    document.addEventListener('keydown', handleKeyboardShortcuts);
}

/* ==================== 导航点击处理 ==================== */
function handleNavClick(event) {
    const target = event.currentTarget;
    const page = target.dataset.page;
    
    if (page) {
        navigateTo(page);
    }
}

/* ==================== 页面导航函数 ==================== */
function navigateTo(pageName) {
    DOM.navItems.forEach(item => {
        item.classList.remove('active');
        if (item.dataset.page === pageName) {
            item.classList.add('active');
        }
    });
    
    DOM.pageSections.forEach(section => {
        section.classList.remove('active');
        if (section.id === `page-${pageName}`) {
            section.classList.add('active');
        }
    });
    
    AppState.currentPage = pageName;
    
    if (pageName === 'interview') {
        resetInterviewConfig();
    }
}

/* ==================== 深色模式切换 ==================== */
function toggleDarkMode() {
    AppState.isDarkMode = !AppState.isDarkMode;
    document.documentElement.setAttribute(
        'data-theme', 
        AppState.isDarkMode ? 'dark' : 'light'
    );
    saveUserSettings();
}

/* ==================== 题目数量更新 ==================== */
function updateQuestionCount(event) {
    const count = event.target.value;
    const countDisplay = document.getElementById('question-count');
    if (countDisplay) {
        countDisplay.textContent = count;
    }
}

/* ==================== 难度选择处理 ==================== */
function handleDifficultySelect(event) {
    const btns = document.querySelectorAll('.diff-btn');
    btns.forEach(btn => btn.classList.remove('active'));
    event.target.classList.add('active');
}

/* ==================== 模式选择处理 ==================== */
function handleModeSelect(event) {
    const options = document.querySelectorAll('.mode-option');
    options.forEach(opt => opt.classList.remove('active'));
    event.currentTarget.classList.add('active');
}

/* ==================== 键盘快捷键处理 ==================== */
function handleKeyboardShortcuts(event) {
    if (event.ctrlKey || event.metaKey) {
        switch(event.key) {
            case '1':
                event.preventDefault();
                navigateTo('home');
                break;
            case '2':
                event.preventDefault();
                navigateTo('interview');
                break;
            case '3':
                event.preventDefault();
                navigateTo('history');
                break;
            case '4':
                event.preventDefault();
                navigateTo('analysis');
                break;
            case '5':
                event.preventDefault();
                navigateTo('settings');
                break;
        }
    }
    
    if (event.key === 'Escape') {
        closeModal();
    }
}

/* ==================== 岗位选择 ==================== */
function selectJob(jobType) {
    navigateTo('interview');
    
    setTimeout(() => {
        const jobSelect = document.getElementById('job-select');
        if (jobSelect) {
            jobSelect.value = jobType;
        }
    }, 100);
}

/* ==================== 重置面试配置 ==================== */
function resetInterviewConfig() {
    const configArea = document.getElementById('interview-config');
    const interviewArea = document.getElementById('interview-area');
    
    if (configArea) configArea.classList.remove('hidden');
    if (interviewArea) interviewArea.classList.add('hidden');
}

/* ==================== 保存用户设置 ==================== */
function saveUserSettings() {
    const settings = {
        isDarkMode: AppState.isDarkMode,
        user: AppState.user
    };
    localStorage.setItem('aiInterviewSettings', JSON.stringify(settings));
    showNotification('设置已保存', 'success');
}

/* ==================== 加载用户设置 ==================== */
function loadUserSettings() {
    const saved = localStorage.getItem('aiInterviewSettings');
    if (saved) {
        try {
            const settings = JSON.parse(saved);
            AppState.isDarkMode = settings.isDarkMode || false;
            AppState.user = { ...AppState.user, ...settings.user };
            
            if (AppState.isDarkMode && DOM.darkModeToggle) {
                DOM.darkModeToggle.checked = true;
                document.documentElement.setAttribute('data-theme', 'dark');
            }
        } catch (e) {
            console.warn('加载设置失败:', e);
        }
    }
}

/* ==================== 初始化历史记录列表 ==================== */
function initHistoryList() {
    const historyList = document.getElementById('history-list');
    if (!historyList) return;
    
    const mockHistory = generateMockHistory();
    historyList.innerHTML = mockHistory.map(item => createHistoryItemHTML(item)).join('');
}

/* ==================== 生成模拟历史数据 ==================== */
function generateMockHistory() {
    return [
        {
            id: 1,
            job: '前端开发工程师',
            date: '2024-01-15 14:30',
            score: 85,
            duration: '15:30',
            questions: 10,
            correct: 8
        },
        {
            id: 2,
            job: '前端开发工程师',
            date: '2024-01-14 10:00',
            score: 78,
            duration: '12:45',
            questions: 10,
            correct: 7
        },
        {
            id: 3,
            job: '全栈开发工程师',
            date: '2024-01-13 16:20',
            score: 92,
            duration: '18:00',
            questions: 15,
            correct: 14
        },
        {
            id: 4,
            job: '后端开发工程师',
            date: '2024-01-12 09:15',
            score: 70,
            duration: '20:30',
            questions: 12,
            correct: 8
        },
        {
            id: 5,
            job: '前端开发工程师',
            date: '2024-01-10 11:00',
            score: 88,
            duration: '14:20',
            questions: 10,
            correct: 9
        }
    ];
}

/* ==================== 创建历史记录项HTML ==================== */
function createHistoryItemHTML(item) {
    const scoreClass = item.score >= 80 ? 'success' : item.score >= 60 ? 'warning' : 'danger';
    
    return `
        <div class="history-item" onclick="viewHistoryDetail(${item.id})">
            <div class="history-info">
                <span class="history-title">${item.job}</span>
                <div class="history-meta">
                    <span><i class="fas fa-calendar"></i> ${item.date}</span>
                    <span><i class="fas fa-clock"></i> 用时 ${item.duration}</span>
                    <span><i class="fas fa-check-circle"></i> ${item.correct}/${item.questions} 正确</span>
                </div>
            </div>
            <div class="history-score">
                <span class="score-badge">${item.score}分</span>
                <i class="fas fa-chevron-right" style="color: var(--text-light);"></i>
            </div>
        </div>
    `;
}

/* ==================== 查看历史详情 ==================== */
function viewHistoryDetail(id) {
    showNotification(`正在加载面试记录 #${id}`, 'info');
}

/* ==================== 关闭弹窗 ==================== */
function closeModal() {
    const modal = document.getElementById('result-modal');
    if (modal) {
        modal.classList.add('hidden');
    }
}

/* ==================== 显示通知 ==================== */
function showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `notification notification-${type}`;
    notification.innerHTML = `
        <i class="fas fa-${type === 'success' ? 'check-circle' : type === 'error' ? 'times-circle' : 'info-circle'}"></i>
        <span>${message}</span>
    `;
    
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 14px 20px;
        background: var(--glass-bg);
        backdrop-filter: blur(20px);
        border: 1px solid var(--glass-border);
        border-radius: var(--radius-md);
        display: flex;
        align-items: center;
        gap: 10px;
        z-index: 2000;
        animation: slideInRight 0.3s ease;
        box-shadow: var(--shadow-medium);
    `;
    
    document.body.appendChild(notification);
    
    setTimeout(() => {
        notification.style.animation = 'fadeOut 0.3s ease';
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}

/* ==================== 添加动画样式 ==================== */
const styleSheet = document.createElement('style');
styleSheet.textContent = `
    @keyframes slideInRight {
        from {
            opacity: 0;
            transform: translateX(100px);
        }
        to {
            opacity: 1;
            transform: translateX(0);
        }
    }
    
    @keyframes fadeOut {
        from { opacity: 1; }
        to { opacity: 0; }
    }
    
    .notification-success i { color: var(--success-color); }
    .notification-error i { color: var(--danger-color); }
    .notification-info i { color: var(--info-color); }
`;
document.head.appendChild(styleSheet);

/* ==================== 保存设置按钮处理 ==================== */
function saveSettings() {
    saveUserSettings();
}

/* ==================== 查看答案详情 ==================== */
function reviewAnswers() {
    closeModal();
    navigateTo('history');
    showNotification('请从历史记录中选择查看详情', 'info');
}

/* ==================== 页面加载完成后初始化 ==================== */
document.addEventListener('DOMContentLoaded', initApp);

/* ==================== 导出全局函数供HTML调用 ==================== */
window.navigateTo = navigateTo;
window.selectJob = selectJob;
window.closeModal = closeModal;
window.saveSettings = saveSettings;
window.reviewAnswers = reviewAnswers;
