(function () {
    const header = document.querySelector(".hdr");
    const navLinks = document.querySelectorAll(".nav__link");
    const searchForm = document.getElementById("header-search");

    function onScroll() {
        if (!header) return;
        if (window.scrollY > 4) header.classList.add("hdr--scrolled");
        else header.classList.remove("hdr--scrolled");
    }
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });

    const HASH_TO_KEY = new Map([
        ["jobs", "jobs"],
        ["events", "events"],
        ["hackathons", "hackathons"],
        ["salary", "salary"],
    ]);

    function resolveKeyFromLocation() {
        const h = (location.hash || "").replace("#", "").toLowerCase();
        if (HASH_TO_KEY.has(h)) return HASH_TO_KEY.get(h);
        const path = location.pathname.toLowerCase();
        if (path.includes("job")) return "jobs";
        if (path.includes("event")) return "events";
        if (path.includes("hack")) return "hackathons";
        if (path.includes("salary") || path.includes("kalkulator")) return "salary";
        return null;
    }

    function applyActive() {
        const key = resolveKeyFromLocation();
        navLinks.forEach(a => a.classList.toggle("is-active", a.dataset.nav === key));
    }
    applyActive();
    window.addEventListener("hashchange", applyActive);

    if (searchForm) {
        searchForm.addEventListener("submit", (e) => {
            e.preventDefault();
            const q = new FormData(searchForm).get("q")?.toString().trim() || "";
            document.dispatchEvent(new CustomEvent("header:search", { detail: { q } }));
        });
    }
})();
