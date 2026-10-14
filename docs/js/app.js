document.documentElement.classList.add("js");

document.querySelectorAll("[data-copy]").forEach((node) => {
    const button = node.querySelector(".copy");
    if (!button) return;
    button.addEventListener("click", async () => {
        const value = node.getAttribute("data-copy") || "";
        try {
            await navigator.clipboard.writeText(value);
            button.textContent = "Copied";
            setTimeout(() => (button.textContent = "Copy"), 1600);
        } catch (e) {
            button.textContent = "Copy";
        }
    });
});

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const hero = document.querySelector("[data-hero]");
if (hero && !reduceMotion) {
    hero.addEventListener("pointermove", (event) => {
        const bounds = hero.getBoundingClientRect();
        hero.style.setProperty("--x", `${event.clientX - bounds.left}px`);
        hero.style.setProperty("--y", `${event.clientY - bounds.top}px`);
    });
}

document.querySelectorAll("[data-reveal]").forEach((node) => {
    if (reduceMotion || !("IntersectionObserver" in window)) {
        node.classList.add("is-in");
        return;
    }
});
if (!reduceMotion && "IntersectionObserver" in window) {
    const observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
            if (entry.isIntersecting) entry.target.classList.add("is-in");
        });
    }, { threshold: 0.18 });
    document.querySelectorAll("[data-reveal]").forEach((node) => observer.observe(node));
}

document.querySelectorAll("[data-stage]").forEach((stage) => {
    const panes = [...stage.querySelectorAll("[data-pane]")];
    const tabs = [...stage.querySelectorAll("[data-tab]")];
    const bar = stage.querySelector("[data-progress]");
    if (panes.length === 0) return;
    let index = 0;
    const show = (next) => {
        index = next % panes.length;
        panes.forEach((pane, i) => pane.classList.toggle("is-active", i === index));
        tabs.forEach((tab, i) => {
            tab.classList.toggle("is-active", i === index);
            tab.setAttribute("aria-selected", i === index ? "true" : "false");
        });
        if (bar && !reduceMotion) {
            bar.classList.remove("is-run");
            void bar.offsetWidth;
            bar.classList.add("is-run");
        }
    };
    tabs.forEach((tab) => {
        tab.addEventListener("click", () => show(Number(tab.getAttribute("data-tab"))));
    });
    if (!reduceMotion) {
        show(0);
        setInterval(() => show(index + 1), 3400);
    }
});

const toggle = document.querySelector("[data-menu]");
if (toggle) {
    toggle.addEventListener("click", () => {
        document.querySelector(".nav")?.classList.toggle("is-open");
        document.querySelector(".header-actions")?.classList.toggle("is-open");
    });
}
