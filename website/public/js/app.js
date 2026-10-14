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

document.querySelectorAll("[data-stage]").forEach((stage) => {
    const panes = [...stage.querySelectorAll("[data-pane]")];
    const tabs = [...stage.querySelectorAll("[data-tab]")];
    const show = (next) => {
        panes.forEach((pane, i) => pane.classList.toggle("is-active", i === next));
        tabs.forEach((tab, i) => {
            tab.classList.toggle("is-active", i === next);
            tab.setAttribute("aria-selected", i === next ? "true" : "false");
        });
    };
    tabs.forEach((tab) => {
        tab.addEventListener("click", () => show(Number(tab.getAttribute("data-tab"))));
    });
});

const toggle = document.querySelector("[data-menu]");
if (toggle) {
    toggle.addEventListener("click", () => {
        document.querySelector(".nav")?.classList.toggle("is-open");
        document.querySelector(".header-actions")?.classList.toggle("is-open");
    });
}
