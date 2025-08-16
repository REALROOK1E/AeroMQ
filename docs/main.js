// 纯静态页面，无需运行时逻辑。保留钩子以便未来扩展。
(() => {
  console.log('AeroMQ docs loaded');

  const key = 'aeromq-docs-lang';
  const body = document.body;
  const btn = document.getElementById('langToggle');

  // init from localStorage or default zh
  const saved = localStorage.getItem(key);
  if (saved === 'en' || saved === 'zh') {
    body.setAttribute('data-lang', saved);
  } else {
    body.setAttribute('data-lang', 'zh');
  }

  function updateButton() {
    const lang = body.getAttribute('data-lang');
    if (lang === 'zh') {
      btn.innerHTML = '<span class="lang-zh">中文</span><span class="divider">｜</span><span class="lang-en">EN</span>';
    } else {
      btn.innerHTML = '<span class="lang-zh">中文</span><span class="divider">｜</span><span class="lang-en"><strong>EN</strong></span>';
    }
  }

  btn?.addEventListener('click', () => {
    const next = body.getAttribute('data-lang') === 'zh' ? 'en' : 'zh';
    body.setAttribute('data-lang', next);
    localStorage.setItem(key, next);
    updateButton();
  });

  updateButton();

  // Back to top
  const topBtn = document.getElementById('toTop');
  const onScroll = () => {
    if (!topBtn) return;
    if (window.scrollY > 320) topBtn.classList.add('show'); else topBtn.classList.remove('show');
  };
  window.addEventListener('scroll', onScroll, { passive: true });
  if (topBtn) topBtn.addEventListener('click', () => window.scrollTo({ top: 0, behavior: 'smooth' }));
  onScroll();

  // Smooth scroll for toc links
  document.querySelectorAll('a.toc-link').forEach(a => {
    a.addEventListener('click', e => {
      const href = a.getAttribute('href');
      if (href && href.startsWith('#')){
        e.preventDefault();
        const t = document.querySelector(href);
        if (t) t.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  });

  // Add copy buttons to code blocks
  document.querySelectorAll('pre').forEach(pre => {
    const button = document.createElement('button');
    button.className = 'btn small copy-btn';
    button.textContent = 'Copy';
    button.style.position = 'absolute';
    button.style.margin = '8px';
    button.style.right = '8px';
    button.style.top = '8px';
    button.addEventListener('click', async () => {
      const text = pre.innerText;
      try {
        await navigator.clipboard.writeText(text);
        button.textContent = 'Copied';
        setTimeout(() => button.textContent = 'Copy', 1200);
      } catch {}
    });
    const wrap = document.createElement('div');
    wrap.style.position = 'relative';
    pre.parentNode.insertBefore(wrap, pre);
    wrap.appendChild(pre);
    wrap.appendChild(button);
  });
})();
