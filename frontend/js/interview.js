/**
 * ============================================================
 * AI模拟面试系统 - 面试核心功能模块
 * 包含面试流程控制、题目管理、计时器等功能
 * ============================================================
 */

/* ==================== 面试状态管理 ==================== */
const InterviewState = {
    isRunning: false,
    isPaused: false,
    currentQuestion: 0,
    totalQuestions: 10,
    difficulty: 'medium',
    jobType: 'frontend',
    mode: 'chat',
    startTime: null,
    elapsedTime: 0,
    timerInterval: null,
    answers: [],
    questions: []
};

/* ==================== 题库数据 ==================== */
const QuestionBank = {
    frontend: {
        easy: [
            { id: 1, question: '请解释什么是HTML语义化标签，并举例说明其优点。', keywords: ['语义化', 'SEO', '可读性', '结构'] },
            { id: 2, question: 'CSS中margin和padding的区别是什么？', keywords: ['外边距', '内边距', '盒模型', '边框'] },
            { id: 3, question: '请解释JavaScript中的数据类型有哪些？', keywords: ['基本类型', '引用类型', 'string', 'number', 'object'] },
            { id: 4, question: '什么是DOM？如何通过JavaScript操作DOM？', keywords: ['文档对象模型', 'getElementById', 'querySelector', '节点'] },
            { id: 5, question: '请解释CSS选择器的优先级规则。', keywords: ['权重', '!important', '内联', 'ID', '类'] }
        ],
        medium: [
            { id: 6, question: '请详细介绍Vue的响应式原理，包括Vue2和Vue3的区别。', keywords: ['Object.defineProperty', 'Proxy', '依赖收集', '派发更新'] },
            { id: 7, question: '解释JavaScript中的闭包是什么？它有什么应用场景？', keywords: ['作用域', '变量引用', '私有变量', '回调函数'] },
            { id: 8, question: '请解释React中的Virtual DOM是如何工作的？', keywords: ['虚拟DOM', 'diff算法', 'Fiber', ' reconciliation'] },
            { id: 9, question: '什么是跨域？有哪些解决方案？', keywords: ['同源策略', 'CORS', 'JSONP', '代理'] },
            { id: 10, question: '请解释CSS Flex布局的主要属性及其作用。', keywords: ['flex-direction', 'justify-content', 'align-items', 'flex-wrap'] }
        ],
        hard: [
            { id: 11, question: '请详细解释JavaScript事件循环机制，包括宏任务和微任务。', keywords: ['Event Loop', '宏任务', '微任务', '调用栈', '任务队列'] },
            { id: 12, question: '如何优化前端性能？请从多个角度进行分析。', keywords: ['加载优化', '渲染优化', '缓存', '代码分割', '懒加载'] },
            { id: 13, question: '请解释Webpack的构建流程和核心概念。', keywords: ['entry', 'output', 'loader', 'plugin', 'bundle'] },
            { id: 14, question: '实现一个简单的发布-订阅模式，并解释其应用场景。', keywords: ['发布', '订阅', '事件中心', '解耦'] },
            { id: 15, question: '请分析前端安全问题及防御措施。', keywords: ['XSS', 'CSRF', 'SQL注入', 'CSP', 'HTTPS'] }
        ]
    },
    backend: {
        easy: [
            { id: 1, question: '请解释什么是RESTful API？', keywords: ['REST', '资源', 'HTTP方法', '无状态'] },
            { id: 2, question: '数据库索引的作用是什么？有什么优缺点？', keywords: ['查询速度', '存储空间', '写入性能', 'B+树'] },
            { id: 3, question: '请解释HTTP和HTTPS的区别。', keywords: ['加密', 'SSL/TLS', '证书', '端口'] }
        ],
        medium: [
            { id: 4, question: '请解释数据库事务的ACID特性。', keywords: ['原子性', '一致性', '隔离性', '持久性'] },
            { id: 5, question: '什么是微服务架构？它有什么优缺点？', keywords: ['服务拆分', '独立部署', '通信', '复杂性'] },
            { id: 6, question: '请解释Redis的数据类型及其应用场景。', keywords: ['String', 'Hash', 'List', 'Set', '缓存'] }
        ],
        hard: [
            { id: 7, question: '如何设计一个高并发系统？', keywords: ['负载均衡', '缓存', '异步', '限流', '熔断'] },
            { id: 8, question: '请解释分布式事务的实现方案。', keywords: ['2PC', 'TCC', 'Saga', '消息队列'] }
        ]
    },
    fullstack: {
        easy: [
            { id: 1, question: '请解释MVC架构模式。', keywords: ['模型', '视图', '控制器', '分离'] },
            { id: 2, question: '什么是前后端分离？有什么优势？', keywords: ['API', '解耦', '并行开发', '维护性'] }
        ],
        medium: [
            { id: 3, question: '请解释JWT的工作原理及其应用场景。', keywords: ['Token', '签名', '认证', '无状态'] },
            { id: 4, question: '如何设计一个用户登录系统？', keywords: ['认证', '授权', 'Session', 'Token', '安全'] }
        ],
        hard: [
            { id: 5, question: '请设计一个简单的电商系统架构。', keywords: ['用户', '商品', '订单', '支付', '库存'] },
            { id: 6, question: '如何实现单点登录(SSO)？', keywords: ['认证中心', 'Token', '跨域', 'Session共享'] }
        ]
    }
};

/* ==================== AI回复模板 ==================== */
const AIResponses = {
    welcome: '你好！我是AI面试官，今天我们将进行{job}岗位的模拟面试。准备好了吗？让我们开始第一个问题：',
    received: '感谢你的回答！让我来分析一下你的回答...\n\n{feedback}\n\n让我们继续下一个问题：',
    skip: '好的，我们跳过这道题。让我们看下一题：',
    nextQuestion: '好的，让我们继续下一题：',
    complete: '面试已结束！感谢你的参与，正在为你生成面试报告...',
    timeout: '面试时间到！请尽快完成当前问题的回答。',
    encouragement: [
        '回答得不错！继续保持！',
        '这个回答很有见地！',
        '思路清晰，很好！',
        '基础知识掌握得不错！',
        '继续加油，你表现得很好！'
    ]
};

/* ==================== 开始面试 ==================== */
function startInterview() {
    const jobSelect = document.getElementById('job-select');
    const questionSlider = document.getElementById('question-slider');
    const diffBtn = document.querySelector('.diff-btn.active');
    const modeOption = document.querySelector('.mode-option.active');
    
    InterviewState.jobType = jobSelect ? jobSelect.value : 'frontend';
    InterviewState.totalQuestions = questionSlider ? parseInt(questionSlider.value) : 10;
    InterviewState.difficulty = diffBtn ? diffBtn.dataset.level : 'medium';
    InterviewState.mode = modeOption ? modeOption.dataset.mode : 'chat';
    InterviewState.currentQuestion = 0;
    InterviewState.answers = [];
    InterviewState.isRunning = true;
    InterviewState.startTime = Date.now();
    InterviewState.elapsedTime = 0;
    
    loadQuestions();
    showInterviewArea();
    startTimer();
    sendWelcomeMessage();
    
    showNotification('面试已开始，祝你成功！', 'success');
}

/* ==================== 加载题目 ==================== */
function loadQuestions() {
    const jobQuestions = QuestionBank[InterviewState.jobType] || QuestionBank.frontend;
    const difficultyQuestions = jobQuestions[InterviewState.difficulty] || jobQuestions.medium;
    
    let questions = [...difficultyQuestions];
    
    while (questions.length < InterviewState.totalQuestions) {
        const randomIndex = Math.floor(Math.random() * difficultyQuestions.length);
        questions.push({ ...difficultyQuestions[randomIndex], id: questions.length + 1 });
    }
    
    questions = shuffleArray(questions).slice(0, InterviewState.totalQuestions);
    
    InterviewState.questions = questions;
}

/* ==================== 数组随机打乱 ==================== */
function shuffleArray(array) {
    const shuffled = [...array];
    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
}

/* ==================== 显示面试区域 ==================== */
function showInterviewArea() {
    const configArea = document.getElementById('interview-config');
    const interviewArea = document.getElementById('interview-area');
    
    if (configArea) configArea.classList.add('hidden');
    if (interviewArea) interviewArea.classList.remove('hidden');
    
    updateProgressDisplay();
    updateJobNameDisplay();
}

/* ==================== 更新进度显示 ==================== */
function updateProgressDisplay() {
    const currentQ = document.getElementById('current-q');
    const totalQ = document.getElementById('total-q');
    
    if (currentQ) currentQ.textContent = InterviewState.currentQuestion + 1;
    if (totalQ) totalQ.textContent = InterviewState.totalQuestions;
}

/* ==================== 更新岗位名称显示 ==================== */
function updateJobNameDisplay() {
    const jobNameEl = document.querySelector('.job-name');
    const jobNames = {
        frontend: '前端开发工程师',
        backend: '后端开发工程师',
        fullstack: '全栈开发工程师',
        algorithm: '算法工程师',
        product: '产品经理'
    };
    
    if (jobNameEl) {
        jobNameEl.textContent = jobNames[InterviewState.jobType] || '前端开发工程师';
    }
}

/* ==================== 启动计时器 ==================== */
function startTimer() {
    const timerEl = document.getElementById('timer');
    
    InterviewState.timerInterval = setInterval(() => {
        InterviewState.elapsedTime = Math.floor((Date.now() - InterviewState.startTime) / 1000);
        const minutes = Math.floor(InterviewState.elapsedTime / 60);
        const seconds = InterviewState.elapsedTime % 60;
        
        if (timerEl) {
            timerEl.textContent = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
        }
    }, 1000);
}

/* ==================== 停止计时器 ==================== */
function stopTimer() {
    if (InterviewState.timerInterval) {
        clearInterval(InterviewState.timerInterval);
        InterviewState.timerInterval = null;
    }
}

/* ==================== 发送欢迎消息 ==================== */
function sendWelcomeMessage() {
    const jobNames = {
        frontend: '前端开发工程师',
        backend: '后端开发工程师',
        fullstack: '全栈开发工程师',
        algorithm: '算法工程师',
        product: '产品经理'
    };
    
    const welcomeMsg = AIResponses.welcome.replace('{job}', jobNames[InterviewState.jobType] || '前端开发');
    
    clearChatMessages();
    addAIMessage(welcomeMsg);
    
    setTimeout(() => {
        showCurrentQuestion();
    }, 500);
}

/* ==================== 清空聊天消息 ==================== */
function clearChatMessages() {
    const chatMessages = document.getElementById('chat-messages');
    if (chatMessages) {
        chatMessages.innerHTML = '';
    }
}

/* ==================== 添加AI消息 ==================== */
function addAIMessage(content, includeQuestion = false) {
    const chatMessages = document.getElementById('chat-messages');
    if (!chatMessages) return;
    
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message ai-message';
    
    let messageHTML = `
        <div class="message-avatar">
            <i class="fas fa-robot"></i>
        </div>
        <div class="message-content">
            <p>${content}</p>
    `;
    
    if (includeQuestion && InterviewState.questions[InterviewState.currentQuestion]) {
        const question = InterviewState.questions[InterviewState.currentQuestion];
        messageHTML += `
            <div class="question-box">
                <strong>问题 ${InterviewState.currentQuestion + 1}：</strong>${question.question}
            </div>
        `;
    }
    
    messageHTML += '</div>';
    messageDiv.innerHTML = messageHTML;
    
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
}

/* ==================== 添加用户消息 ==================== */
function addUserMessage(content) {
    const chatMessages = document.getElementById('chat-messages');
    if (!chatMessages) return;
    
    const messageDiv = document.createElement('div');
    messageDiv.className = 'message user-message';
    messageDiv.innerHTML = `
        <div class="message-avatar">
            <i class="fas fa-user"></i>
        </div>
        <div class="message-content">
            <p>${content}</p>
        </div>
    `;
    
    chatMessages.appendChild(messageDiv);
    scrollToBottom();
}

/* ==================== 滚动到底部 ==================== */
function scrollToBottom() {
    const chatMessages = document.getElementById('chat-messages');
    if (chatMessages) {
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }
}

/* ==================== 显示当前题目 ==================== */
function showCurrentQuestion() {
    if (InterviewState.currentQuestion >= InterviewState.totalQuestions) {
        finishInterview();
        return;
    }
    
    const question = InterviewState.questions[InterviewState.currentQuestion];
    if (question) {
        addAIMessage('', true);
    }
}

/* ==================== 提交回答 ==================== */
function submitAnswer() {
    const answerInput = document.getElementById('answer-input');
    if (!answerInput) return;
    
    const answer = answerInput.value.trim();
    if (!answer) {
        showNotification('请输入你的回答', 'error');
        return;
    }
    
    addUserMessage(answer);
    answerInput.value = '';
    
    const question = InterviewState.questions[InterviewState.currentQuestion];
    const score = evaluateAnswer(answer, question);
    
    InterviewState.answers.push({
        questionId: question.id,
        question: question.question,
        answer: answer,
        score: score,
        keywords: question.keywords
    });
    
    InterviewState.currentQuestion++;
    updateProgressDisplay();
    
    if (InterviewState.currentQuestion >= InterviewState.totalQuestions) {
        setTimeout(() => {
            finishInterview();
        }, 1000);
    } else {
        setTimeout(() => {
            const feedback = generateFeedback(score);
            const encouragement = AIResponses.encouragement[Math.floor(Math.random() * AIResponses.encouragement.length)];
            addAIMessage(`${encouragement}\n\n${feedback}`);
            
            setTimeout(() => {
                showCurrentQuestion();
            }, 500);
        }, 500);
    }
}

/* ==================== 评估回答得分 ==================== */
function evaluateAnswer(answer, question) {
    if (!question || !question.keywords) return 70;
    
    let matchedKeywords = 0;
    const answerLower = answer.toLowerCase();
    
    question.keywords.forEach(keyword => {
        if (answerLower.includes(keyword.toLowerCase())) {
            matchedKeywords++;
        }
    });
    
    const keywordScore = (matchedKeywords / question.keywords.length) * 40;
    const lengthScore = Math.min(answer.length / 100, 1) * 30;
    const baseScore = 30;
    
    return Math.min(Math.round(baseScore + keywordScore + lengthScore), 100);
}

/* ==================== 生成反馈 ==================== */
function generateFeedback(score) {
    if (score >= 90) {
        return '你的回答非常全面，涵盖了所有关键点！';
    } else if (score >= 75) {
        return '回答得不错，基本涵盖了主要内容，可以再深入一些细节。';
    } else if (score >= 60) {
        return '回答基本正确，但还可以更详细一些，建议补充更多相关知识点。';
    } else {
        return '这个问题的回答还有提升空间，建议复习相关知识点。';
    }
}

/* ==================== 跳过问题 ==================== */
function skipQuestion() {
    const question = InterviewState.questions[InterviewState.currentQuestion];
    
    InterviewState.answers.push({
        questionId: question.id,
        question: question.question,
        answer: '[跳过]',
        score: 0,
        keywords: question.keywords
    });
    
    InterviewState.currentQuestion++;
    updateProgressDisplay();
    
    addAIMessage(AIResponses.skip);
    
    setTimeout(() => {
        showCurrentQuestion();
    }, 500);
}

/* ==================== 结束面试 ==================== */
function endInterview() {
    if (confirm('确定要结束当前面试吗？')) {
        finishInterview();
    }
}

/* ==================== 完成面试 ==================== */
function finishInterview() {
    InterviewState.isRunning = false;
    stopTimer();
    
    addAIMessage(AIResponses.complete);
    
    setTimeout(() => {
        showInterviewResult();
    }, 1500);
}

/* ==================== 显示面试结果 ==================== */
function showInterviewResult() {
    const result = calculateFinalResult();
    const modal = document.getElementById('result-modal');
    
    if (modal) {
        const scoreValue = modal.querySelector('.score-value');
        const correctValue = modal.querySelector('.result-value.correct');
        const timeValue = modal.querySelectorAll('.result-value')[1];
        const beatValue = modal.querySelectorAll('.result-value')[2];
        const feedbackText = modal.querySelector('.result-feedback p');
        
        if (scoreValue) scoreValue.textContent = result.avgScore;
        if (correctValue) correctValue.textContent = `${result.correctCount}/${result.totalQuestions}`;
        if (timeValue) timeValue.textContent = formatTime(InterviewState.elapsedTime);
        if (beatValue) beatValue.textContent = `${result.beatPercent}%`;
        if (feedbackText) feedbackText.textContent = result.feedback;
        
        modal.classList.remove('hidden');
    }
    
    saveInterviewRecord(result);
}

/* ==================== 计算最终结果 ==================== */
function calculateFinalResult() {
    const totalScore = InterviewState.answers.reduce((sum, a) => sum + a.score, 0);
    const avgScore = Math.round(totalScore / InterviewState.answers.length);
    const correctCount = InterviewState.answers.filter(a => a.score >= 60).length;
    const beatPercent = Math.min(Math.round(avgScore * 0.9 + Math.random() * 10), 99);
    
    let feedback = '';
    if (avgScore >= 85) {
        feedback = '表现优秀！你对相关知识掌握得很好，继续保持！';
    } else if (avgScore >= 70) {
        feedback = '整体表现良好，基础知识扎实。建议针对薄弱环节进行针对性练习。';
    } else if (avgScore >= 60) {
        feedback = '基本合格，但还有提升空间。建议系统复习相关知识点，多做练习。';
    } else {
        feedback = '本次面试表现需要加强。建议从基础开始系统学习，多加练习。';
    }
    
    return {
        avgScore,
        correctCount,
        totalQuestions: InterviewState.totalQuestions,
        beatPercent,
        feedback,
        duration: InterviewState.elapsedTime
    };
}

/* ==================== 格式化时间 ==================== */
function formatTime(seconds) {
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

/* ==================== 保存面试记录 ==================== */
function saveInterviewRecord(result) {
    const record = {
        id: Date.now(),
        job: getJobDisplayName(InterviewState.jobType),
        date: new Date().toLocaleString('zh-CN'),
        score: result.avgScore,
        duration: formatTime(result.duration),
        questions: result.totalQuestions,
        correct: result.correctCount,
        answers: InterviewState.answers
    };
    
    let records = JSON.parse(localStorage.getItem('interviewRecords') || '[]');
    records.unshift(record);
    records = records.slice(0, 50);
    localStorage.setItem('interviewRecords', JSON.stringify(records));
}

/* ==================== 获取岗位显示名称 ==================== */
function getJobDisplayName(jobType) {
    const names = {
        frontend: '前端开发工程师',
        backend: '后端开发工程师',
        fullstack: '全栈开发工程师',
        algorithm: '算法工程师',
        product: '产品经理'
    };
    return names[jobType] || '前端开发工程师';
}

/* ==================== 导出全局函数 ==================== */
window.startInterview = startInterview;
window.submitAnswer = submitAnswer;
window.skipQuestion = skipQuestion;
window.endInterview = endInterview;
