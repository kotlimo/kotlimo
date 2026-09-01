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

const toggle = document.querySelector("[data-menu]");
if (toggle) {
    toggle.addEventListener("click", () => {
        document.querySelector(".nav")?.classList.toggle("is-open");
        document.querySelector(".header-actions")?.classList.toggle("is-open");
    });
}
