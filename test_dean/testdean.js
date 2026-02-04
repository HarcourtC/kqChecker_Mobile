/**
 * XJTU JWC Scraper (WAF Bypass Version)
 * 原理：通过正则提取 challenge 参数，模拟 HTTP 验证握手，获取 Cookie
 */

const axios = require('axios');
const cheerio = require('cheerio');
const fs = require('fs');
const path = require('path');
const https = require('https');

const CONFIG = {
    BASE_URL: 'https://jwc.xjtu.edu.cn',
    TARGETS: [
        { name: '课程安排', type: 'kcap', url: '/jxxx/jxtz2/kcap.htm' },
        { name: '竞赛大创', type: 'jsdc', url: '/jxxx/jxtz2/jsdc.htm' },
        { name: '竞赛安排', type: 'jsap', url: '/jxxx/jxtz2/jsap.htm' }
    ],
    SAVE_PATH: '/var/www/api/xjtudean.json',
    USER_AGENT: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
};

// 忽略 HTTPS 证书错误
const agent = new https.Agent({ rejectUnauthorized: false });

// 创建一个持久化的 Axios 实例 (就像浏览器的 Session)
const client = axios.create({
    baseURL: CONFIG.BASE_URL,
    timeout: 10000,
    httpsAgent: agent,
    headers: {
        'User-Agent': CONFIG.USER_AGENT,
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
        'Connection': 'keep-alive'
    },
    // 禁止自动重定向，方便我们需要处理 Cookie 时手动接管
    maxRedirects: 5 
});

// 全局 Cookie 容器
let globalCookie = '';

/**
 * 核心逻辑：解决 WAF 验证
 * @param {string} htmlWaf - 包含 challenge 的 HTML 内容
 */
async function solveWaf(htmlWaf) {
    console.log('[WAF] 正在解析验证参数...');
    
    // 1. 正则提取参数
    const idMatch = htmlWaf.match(/var challengeId = "(.*?)";/);
    const ansMatch = htmlWaf.match(/var answer = (\d+);/);

    if (!idMatch || !ansMatch) {
        throw new Error('无法解析 WAF 参数，可能页面结构已变');
    }

    const payload = {
        challenge_id: idMatch[1],
        answer: parseInt(ansMatch[1]),
        browser_info: {
            userAgent: CONFIG.USER_AGENT,
            language: "zh-CN",
            platform: "Linux x86_64",
            cookieEnabled: true,
            hardwareConcurrency: 4,
            deviceMemory: 8,
            timezone: "Asia/Shanghai"
        }
    };

    // 2. 发送验证请求
    console.log('[WAF] 发送验证请求...');
    const res = await client.post('/dynamic_challenge', payload, {
        headers: {
            'Content-Type': 'application/json',
            'Referer': CONFIG.BASE_URL + '/index.htm', // 伪造来源
            'Origin': CONFIG.BASE_URL
        }
    });

    if (res.data && res.data.success) {
        console.log(`[WAF] 验证成功! 获取 Client ID: ${res.data.client_id}`);
        return `client_id=${res.data.client_id}; path=/; max-age=86400`;
    } else {
        throw new Error('WAF 验证接口返回失败');
    }
}

async function fetchNews() {
    let allNews = [];
    const today = new Date();

    // 第一次尝试请求首页，目的是触发 WAF 并拿到 Cookie
    try {
        console.log('[Init] 正在进行首次握手...');
        const firstRes = await client.get('/index.htm');
        
        // 如果返回的内容包含 loader，说明触发了 WAF
        if (firstRes.data.includes('class="loader"')) {
            globalCookie = await solveWaf(firstRes.data);
        } else {
            console.log('[Init] 未触发 WAF，直接通过。');
        }
    } catch (e) {
        console.error('[Init Error] 初始化连接失败:', e.message);
        return; // 初始化失败则退出
    }

    // 开始正式抓取
    for (const target of CONFIG.TARGETS) {
        try {
            console.log(`正在请求: ${target.name}...`);
            
            // 带上 Cookie 访问
            const { data } = await client.get(target.url, {
                headers: { 'Cookie': globalCookie }
            });

            // 再次检查是否中途又触发了 WAF (Cookie 过期等情况)
            if (typeof data === 'string' && data.includes('class="loader"')) {
                console.log(`[Warn] ${target.name} 再次触发 WAF，尝试重新验证...`);
                globalCookie = await solveWaf(data);
                // 递归重试一次 (略，简单起见直接跳过或报错)
                continue; 
            }

            const $ = cheerio.load(data);
            const items = $('ul.list li'); // 如果这里找不到，可以换 li[id^="line_u"]
            
            console.log(`  > 抓取到 ${items.length} 条数据`);

            items.each((i, el) => {
                const a = $(el).find('a');
                const span = $(el).find('span');
                const rawHref = a.attr('href');
                const title = a.text().trim();
                const date = span.text().trim();

                if (rawHref && title) {
                    // URL 补全
                    let fullUrl = rawHref.replace(/(\.\.\/)+/, CONFIG.BASE_URL + '/');
                    // 清理多余的斜杠
                    fullUrl = fullUrl.replace(/([^:]\/)\/+/g, "$1");

                    // ID 提取
                    const idMatch = rawHref.match(/\/(\d+)\.htm/);
                    const id = idMatch ? idMatch[1] : Math.random().toString(36).substr(2, 9);

                    // 新消息判定
                    const newsDate = new Date(date);
                    const diffDays = Math.ceil(Math.abs(today - newsDate) / (1000 * 60 * 60 * 24));

                    allNews.push({
                        id: id,
                        type: target.type,
                        category: target.name,
                        title: title,
                        url: fullUrl,
                        date: date,
                        isNew: diffDays <= 3
                    });
                }
            });

        } catch (e) {
            console.error(`[Error] ${target.name}: ${e.message}`);
        }
    }

    // 保存逻辑
    if (allNews.length > 0) {
        allNews.sort((a, b) => new Date(b.date) - new Date(a.date));
        const finalData = allNews.slice(0, 40);

        const output = {
            status: "success",
            meta: {
                updateTime: new Date().toISOString(),
                total: finalData.length,
                method: "WAF Reverse Engineering"
            },
            data: finalData
        };

        const dir = path.dirname(CONFIG.SAVE_PATH);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
        fs.writeFileSync(CONFIG.SAVE_PATH, JSON.stringify(output, null, 2));
        console.log(`[Success] 数据保存成功: ${CONFIG.SAVE_PATH}`);
    } else {
        console.log('[Fail] 0 条数据被抓取');
    }
}

fetchNews();