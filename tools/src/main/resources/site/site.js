/*
 * Progressive enhancement only. Every page is complete and useful with this file blocked:
 *
 *  1. A year picker rendered as a list of links is collapsed into a <select> that navigates.
 *  2. A "copy link" button is added next to each page heading.
 *
 * No fetches, no framework, no state. If anything here throws, the page is still the page.
 */
(function () {
  "use strict";

  function upgradeYearPickers() {
    var pickers = document.querySelectorAll("[data-year-picker]");
    for (var i = 0; i < pickers.length; i++) {
      upgradeYearPicker(pickers[i]);
    }
  }

  function upgradeYearPicker(picker) {
    var links = picker.querySelectorAll("a[href]");
    if (links.length < 2) {
      return;
    }

    var label = picker.getAttribute("data-label") || "Year";
    var id = "year-picker-" + Math.random().toString(36).slice(2, 8);

    var select = document.createElement("select");
    select.id = id;
    for (var i = 0; i < links.length; i++) {
      var option = document.createElement("option");
      option.value = links[i].getAttribute("href");
      option.textContent = links[i].textContent.trim();
      if (links[i].getAttribute("aria-current") === "page") {
        option.selected = true;
      }
      select.appendChild(option);
    }
    select.addEventListener("change", function () {
      if (select.value) {
        window.location.href = select.value;
      }
    });

    var form = document.createElement("form");
    form.addEventListener("submit", function (event) {
      event.preventDefault();
      window.location.href = select.value;
    });

    var caption = document.createElement("label");
    caption.setAttribute("for", id);
    caption.textContent = label;

    var go = document.createElement("button");
    go.type = "submit";
    go.textContent = "Go";

    form.appendChild(caption);
    form.appendChild(select);
    form.appendChild(go);

    var list = picker.querySelector("ul");
    if (list) {
      list.hidden = true;
    }
    picker.insertBefore(form, picker.firstChild);
  }

  function addCopyLink() {
    if (!navigator.clipboard) {
      return;
    }
    var heading = document.querySelector("main h1");
    if (!heading) {
      return;
    }
    var button = document.createElement("button");
    button.type = "button";
    button.className = "copy-link";
    button.textContent = "Copy link";
    button.addEventListener("click", function () {
      navigator.clipboard.writeText(window.location.href).then(
        function () {
          button.textContent = "Copied";
          window.setTimeout(function () {
            button.textContent = "Copy link";
          }, 1500);
        },
        function () {
          button.textContent = "Press Ctrl+C";
        }
      );
    });
    heading.appendChild(document.createTextNode(" "));
    heading.appendChild(button);
  }

  try {
    upgradeYearPickers();
    addCopyLink();
  } catch (error) {
    /* Enhancement only: never break the underlying page. */
  }
})();
