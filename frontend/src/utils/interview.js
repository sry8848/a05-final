export const QuestionBank = {
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
  },
  algorithm: {
    easy: [
      { id: 1, question: '请解释时间复杂度和空间复杂度的概念。', keywords: ['大O表示法', '运行时间', '内存占用', '效率'] },
      { id: 2, question: '什么是数组？数组和链表的区别是什么？', keywords: ['连续内存', '随机访问', '插入删除', '缓存'] }
    ],
    medium: [
      { id: 3, question: '请解释常见的排序算法及其时间复杂度。', keywords: ['快排', '归并', '堆排序', '稳定'] },
      { id: 4, question: '什么是动态规划？请举例说明。', keywords: ['最优子结构', '重叠子问题', '状态转移', '备忘录'] }
    ],
    hard: [
      { id: 5, question: '请解释深度优先搜索和广度优先搜索的区别及应用场景。', keywords: ['栈', '队列', '最短路径', '连通性'] }
    ]
  },
  product: {
    easy: [
      { id: 1, question: '什么是产品需求文档(PRD)？它包含哪些内容？', keywords: ['功能描述', '用户故事', '验收标准', '原型'] },
      { id: 2, question: '请解释用户体验(UX)和用户界面(UI)的区别。', keywords: ['交互', '视觉', '流程', '感受'] }
    ],
    medium: [
      { id: 3, question: '如何进行竞品分析？', keywords: ['功能对比', '市场定位', '优劣势', '差异化'] },
      { id: 4, question: '请解释MVP（最小可行产品）的概念及其意义。', keywords: ['核心功能', '快速验证', '迭代', '成本'] }
    ],
    hard: [
      { id: 5, question: '如何制定产品路线图？', keywords: ['优先级', '里程碑', '资源', '市场'] }
    ]
  }
}

export const AIResponses = {
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
}

export const getJobDisplayName = (jobType) => {
  const names = {
    frontend: '前端开发工程师',
    backend: '后端开发工程师',
    fullstack: '全栈开发工程师',
    algorithm: '算法工程师',
    product: '产品经理'
  }
  return names[jobType] || '前端开发工程师'
}
