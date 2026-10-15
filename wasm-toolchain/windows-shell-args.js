"use strict";

function quoteWindowsShellArguments(args) {
  return args.map((argument) => {
    const value = String(argument);
    if (value.includes('"')) {
      throw new Error("Windows batch arguments must not contain literal double quotes");
    }
    return /[\s&|<>^()]/.test(value) ? `"${value}"` : value;
  });
}

module.exports = { quoteWindowsShellArguments };
