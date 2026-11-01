from playwright.sync_api import sync_playwright

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    context = browser.new_context(viewport={"width": 960, "height": 540})
    context.add_init_script(
        """(() => {
          const install = () => {
            if (!document.documentElement || document.getElementById('wallpaper-menu-viewport-style')) return;
            const style = document.createElement('style');
            style.id = 'wallpaper-menu-viewport-style';
            style.textContent = 'p { padding-left: 0 !important; padding-right: 0 !important; } #object_clock { width: max-content !important; margin: auto !important; }';
            document.documentElement.appendChild(style);
          };
          new MutationObserver(install).observe(document, { childList: true, subtree: true });
          install();
        })()"""
    )
    page = context.new_page()
    page.goto("file:///E:/Steam/steamapps/workshop/content/431960/3007669757/index.html")
    page.wait_for_timeout(800)
    result = page.locator("#object_clock").evaluate(
        "element => { const rect = element.getBoundingClientRect(); return {x: rect.x, width: rect.width, viewWidth: innerWidth, style: !!document.getElementById('wallpaper-menu-viewport-style')}; }"
    )
    print(result)
    assert result["style"]
    assert abs(result["x"] + result["width"] / 2 - result["viewWidth"] / 2) <= 2
    assert result["x"] >= 0 and result["x"] + result["width"] <= result["viewWidth"]
    page.screenshot(path="build/web-layout-verified.png")
    browser.close()
