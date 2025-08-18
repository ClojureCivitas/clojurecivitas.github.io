;;https://stackoverflow.com/questions/11616630/how-can-i-print-a-circular-structure-in-a-json-like-format
JSON.safeStringify = (obj, indent = 2) => {
    let cache = [];
    const retVal = JSON.stringify(
        obj,
        (key, value) =>
        typeof value === "object" && value !== null
            ? cache.includes(value)
            ? undefined // Duplicate reference found, discard key
            : cache.push(value) && value // Store value in our collection
        : value,
        indent
    );
    cache = null;
    return retVal;
};
