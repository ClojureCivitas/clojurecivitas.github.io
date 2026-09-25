(function () {
    // 1. Locate and parse the Next.js data script tag
    const scriptTag = document.getElementById('__NEXT_DATA__');
    if (!scriptTag) {
        console.error("Could not find __NEXT_DATA__ script tag.");
        return;
    }

    const jsonData = JSON.parse(scriptTag.textContent);
    const billionaires = jsonData?.props?.pageProps?.data?.billionairesData?.billionaires;

    if (!billionaires || billionaires.length === 0) {
        console.error("Could not find billionaires data structure.");
        return;
    }

    // 2. Convert the raw array into a formatted JSON string
    const jsonString = JSON.stringify(billionaires, null, 2);

    // 3. Force browser file download
    const blob = new Blob([jsonString], { type: 'application/json;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");

    link.setAttribute("href", url);
    link.setAttribute("download", "forbes_billionaires.json");
    link.style.visibility = 'hidden';

    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    console.log(`Successfully exported all ${billionaires.length} raw JSON records!`);
})();
