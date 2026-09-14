/*
 * Progressive enhancement only. Every page is complete and useful with this file blocked:
 *
 *  1. A year picker rendered as a list of links is collapsed into a <select> that navigates.
 *  2. A "copy link" button is added next to each page heading.
 *  3. A compare page grows a T+N settlement form. Without it the page still names the CLI command
 *     that answers the same question.
 *
 * No framework, no state, no build step. If anything here throws, the page is still the page.
 *
 * The settlement algorithm below is the reference implementation for browsers and is specified in
 * spec/SPEC.md ("Query API" -> "Settlement in the browser"). It must agree with JointDateStream;
 * /compare/settlement-selftest.html scores it against a fixture of answers computed in Java.
 */
(function () {
  "use strict";

  var DAYS = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];
  var DAY = 86400000;
  var MAX_SEARCH_DAYS = 366;

  // ---------------------------------------------------------------- dates

  function toDay(iso) {
    return Date.UTC(+iso.slice(0, 4), +iso.slice(5, 7) - 1, +iso.slice(8, 10));
  }

  function toIso(day) {
    return new Date(day).toISOString().slice(0, 10);
  }

  function dayName(day) {
    return DAYS[new Date(day).getUTCDay()];
  }

  // ------------------------------------------------------- calendar model

  /* A calendar's weekend as a list of effective-dated periods; the LAST one covering a date wins,
     and a date no period covers has no weekend days (WeekendPolicy.daysOn). */
  function periodsOf(policy) {
    if (!policy) {
      return [];
    }
    if (policy.periods && policy.periods.length) {
      return policy.periods;
    }
    return [{ days: policy.days || [] }];
  }

  function isWeekend(calendar, iso, name) {
    for (var i = calendar.weekend.length - 1; i >= 0; i--) {
      var period = calendar.weekend[i];
      if ((!period.from || iso >= period.from) && (!period.to || iso <= period.to)) {
        return (period.days || []).indexOf(name) >= 0;
      }
    }
    return false;
  }

  /* A date is a business day when it carries no CLOSED row and is not a weekend day under the
     policy in effect on it. EARLY_CLOSE days are business days. */
  function isBusinessDay(calendar, iso, name) {
    return !calendar.closed[iso] && !isWeekend(calendar, iso, name);
  }

  /*
   * The joint T+N settlement date: walk forward day by day, counting only days EVERY calendar
   * trades on. n === 0 returns the trade date unchanged; otherwise the trade date is never counted,
   * so T+1 from a Friday is the following Monday. Mirrors DateStream.nthBusinessDay over
   * JointDateStream.isBusinessDay, bound included.
   */
  function settle(calendars, tradeIso, n) {
    var day = toDay(tradeIso);
    var steps = [];
    var remaining = n;
    var guard = MAX_SEARCH_DAYS * n + MAX_SEARCH_DAYS;
    var iso = tradeIso;
    while (remaining > 0) {
      if (guard-- <= 0) {
        throw new Error("No settlement date within " + MAX_SEARCH_DAYS * n + " days");
      }
      day += DAY;
      iso = toIso(day);
      var name = dayName(day);
      var closed = [];
      for (var i = 0; i < calendars.length; i++) {
        if (!calendars[i].years[iso.slice(0, 4)]) {
          throw new Error(calendars[i].id + " has no published data for " + iso);
        }
        if (!isBusinessDay(calendars[i], iso, name)) {
          closed.push(calendars[i].id);
        }
      }
      steps.push({ date: iso, closed: closed });
      if (!closed.length) {
        remaining--;
      }
    }
    return { settles: iso, steps: steps };
  }

  // ------------------------------------------------------------- fetching

  var cache = {};

  function fetchJson(url) {
    return window.fetch(url).then(function (response) {
      if (!response.ok) {
        throw new Error(response.status + " " + url);
      }
      return response.json();
    });
  }

  /* Loads a calendar's weekend policy and the CLOSED rows of the requested years, once each. */
  function load(api, id, years) {
    var calendar = cache[id];
    if (!calendar) {
      calendar = cache[id] = { id: id, closed: {}, years: {}, pending: {} };
      calendar.pending.manifest = fetchJson(api + id + "/manifest.json").then(function (manifest) {
        calendar.weekend = periodsOf(manifest.weekend_policy);
        calendar.from = manifest.range_start;
        calendar.to = manifest.range_end;
      });
    }
    var jobs = [calendar.pending.manifest];
    years.forEach(function (year) {
      var key = String(year);
      if (!calendar.pending[key]) {
        calendar.pending[key] = fetchJson(api + id + "/" + key + ".json").then(
          function (document_) {
            (document_.events || []).forEach(function (event) {
              if (event.type === "CLOSED") {
                calendar.closed[event.date] = true;
              }
            });
            calendar.years[key] = true;
          },
          function () {
            /* Out of coverage: settle() reports it against the date that needed the year. */
          }
        );
      }
      jobs.push(calendar.pending[key]);
    });
    return Promise.all(jobs).then(function () {
      return calendar;
    });
  }

  function yearsFor(iso) {
    var year = +iso.slice(0, 4);
    return [year, year + 1];
  }

  // -------------------------------------------------------- settlement UI

  function upgradeSettlementForms() {
    if (!window.fetch || !window.Promise) {
      return;
    }
    var mounts = document.querySelectorAll("[data-settlement]");
    for (var i = 0; i < mounts.length; i++) {
      mountSettlement(mounts[i]);
    }
  }

  /* The form's markup is fixed and authored here, so innerHTML is safe; every value that comes
     from the page or the API is written with textContent below. */
  var FORM =
    '<form class="settlement-form">' +
    '<label for="sd">Trade date</label><input id="sd" type="date" required>' +
    '<label for="sn">T+</label><input id="sn" type="number" min="0" max="10" value="2">' +
    "<button type=\"submit\">Settle</button></form>" +
    '<div class="settlement-result" role="status"></div>';

  function mountSettlement(mount) {
    var api = mount.getAttribute("data-api");
    var ids = [mount.getAttribute("data-a"), mount.getAttribute("data-b")];
    var min = mount.getAttribute("data-min");
    var max = mount.getAttribute("data-max");

    mount.innerHTML = FORM;
    var form = mount.querySelector("form");
    var date = mount.querySelector("#sd");
    var n = mount.querySelector("#sn");
    var result = mount.querySelector(".settlement-result");
    date.min = min;
    date.max = max;
    date.value = max >= "2026-02-25" && min <= "2026-02-25" ? "2026-02-25" : min;

    form.addEventListener("submit", function (event) {
      event.preventDefault();
      var tradeIso = date.value;
      var steps = Math.max(0, Math.min(10, parseInt(n.value, 10) || 0));
      if (!tradeIso) {
        return;
      }
      result.textContent = "Fetching…";
      Promise.all(
        ids.map(function (id) {
          return load(api, id, yearsFor(tradeIso));
        })
      )
        .then(function (calendars) {
          render(result, ids, tradeIso, steps, settle(calendars, tradeIso, steps));
        })
        .catch(function (error) {
          result.textContent = "Could not settle: " + error.message;
        });
    });
  }

  function render(result, ids, tradeIso, n, answer) {
    result.textContent = "";
    var headline = document.createElement("p");
    headline.className = "settlement-answer";
    headline.textContent =
      "T+" + n + " from " + tradeIso + " settles " + answer.settles + " on " + ids.join(" + ");
    result.appendChild(headline);

    var list = document.createElement("ol");
    list.className = "settlement-steps";
    var counted = 0;
    answer.steps.forEach(function (step) {
      var item = document.createElement("li");
      if (step.closed.length) {
        item.className = "closed";
        item.textContent = step.date + " — closed in " + step.closed.join(", ");
      } else {
        counted++;
        item.textContent = step.date + " — business day " + counted + " of " + n;
      }
      list.appendChild(item);
    });
    if (list.children.length) {
      result.appendChild(list);
    }
  }

  // -------------------------------------------------------- self-test page

  function runSelfTest() {
    var mount = document.querySelector("[data-settlement-selftest]");
    if (!mount || !window.fetch || !window.Promise) {
      return;
    }
    var api = mount.getAttribute("data-api");
    var fixture = JSON.parse(document.getElementById("settlement-fixture").textContent);
    var needed = {};
    fixture.cases.forEach(function (one) {
      [one.a, one.b].forEach(function (id) {
        needed[id] = (needed[id] || []).concat(yearsFor(one.trade_date));
      });
    });
    mount.textContent = "Fetching " + Object.keys(needed).length + " calendars…";
    Promise.all(
      Object.keys(needed).map(function (id) {
        return load(api, id, needed[id]);
      })
    )
      .then(function () {
        var failures = [];
        fixture.cases.forEach(function (one) {
          var calendars = [cache[one.a], cache[one.b]];
          var got;
          try {
            got = settle(calendars, one.trade_date, one.n);
          } catch (error) {
            failures.push(describe(one) + ": threw " + error.message);
            return;
          }
          if (got.settles !== one.settles) {
            failures.push(describe(one) + ": expected " + one.settles + ", got " + got.settles);
          }
          var skipped = got.steps.filter(function (step) {
            return step.closed.length;
          });
          var expected = JSON.stringify(one.skipped);
          var actual = JSON.stringify(skipped);
          if (expected !== actual) {
            failures.push(describe(one) + ": skipped days " + actual + " != " + expected);
          }
        });
        report(mount, fixture.cases.length, failures);
      })
      .catch(function (error) {
        mount.textContent =
          "Could not run: " + error.message + " — serve the site over HTTP, not file://.";
      });
  }

  function describe(one) {
    return one.a + "+" + one.b + " T+" + one.n + " from " + one.trade_date;
  }

  function report(mount, total, failures) {
    mount.textContent = "";
    var verdict = document.createElement("p");
    verdict.className = "badge " + (failures.length ? "warn" : "ok");
    verdict.textContent = failures.length
      ? failures.length + " of " + total + " cases FAILED"
      : total + " of " + total + " cases passed";
    mount.appendChild(verdict);
    if (failures.length) {
      var list = document.createElement("ul");
      failures.forEach(function (failure) {
        var item = document.createElement("li");
        item.textContent = failure;
        list.appendChild(item);
      });
      mount.appendChild(list);
    }
  }

  // ------------------------------------------------------------ year picker

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
    upgradeSettlementForms();
    runSelfTest();
  } catch (error) {
    /* Enhancement only: never break the underlying page. */
  }
})();
