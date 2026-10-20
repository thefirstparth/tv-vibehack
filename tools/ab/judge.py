#!/usr/bin/env python3
"""judge.py <log> [--noise MS]: the side-by-side numbers from a night.sh log and the qualifying rule.

The rule (examples/xiaomi-mitv-moor2/BENCHMARKS.md): Home return median no more than max(17 ms, the noise) slower and
never more than 30 ms slower; cold start no more than 20 ms slower; janky frames no more than 0.5 points higher; memory
no more than 3 MB higher; no crash or freeze. The noise is the gap between the C1 and C2 slots' Home return medians
(pattern CNC); a CN log takes it from --noise (the weekday test's). Only rounds with both builds in them count.
Frame-time percentiles (grid run) are reported, not judged. Cold start is judged on the median of every cold start
of a build (slots with colds=[...] give five each).
"""
import re
import statistics as st
import sys

# (older logs, before the frame-time fields, called the new build W; both still read)
SLOT = re.compile(r"(C1|C2|C|N|W)-r(\d+) cold=(\d*)(?: colds=\[([\d,]*)\])? janky=\(([\d.]*)%?\)(?: frames=(\d*) p50=(\d*)ms)? p90=(\d*)ms"
                  r"(?: p95=(\d*)ms p99=(\d*)ms)? pssKB=(\d*) rets=\[([\d,]*)\] state=\[(.*?)\]")


def num(s, kind=int):
    return kind(s) if s else None


def main():
    args = sys.argv[1:]
    noise_arg = None
    if "--noise" in args:
        i = args.index("--noise")
        noise_arg = float(args[i + 1])
        del args[i:i + 2]
    rows = []
    for line in open(args[0]):
        m = SLOT.search(line)
        if not m:
            continue
        g = m.groups()
        colds = [int(x) for x in (g[3] or "").split(",") if x] or ([num(g[2])] if g[2] else [])
        rows.append(dict(slot="N" if g[0] == "W" else g[0], r=int(g[1]), cold=num(g[2]), colds=colds,
                         janky=num(g[4], float), frames=num(g[5]), p50=num(g[6]), p90=num(g[7]), p95=num(g[8]),
                         p99=num(g[9]), pss=num(g[10]), rets=[int(x) for x in g[11].split(",") if x], state=g[12].split()))
    rounds = sorted(r for r in set(x["r"] for x in rows)
                    if any(x["r"] == r and x["slot"] == "N" for x in rows)
                    and any(x["r"] == r and x["slot"].startswith("C") for x in rows))
    use = [x for x in rows if x["r"] in rounds]
    cur = [x for x in use if x["slot"].startswith("C")]
    new = [x for x in use if x["slot"] == "N"]
    if not rounds:
        print("VERDICT: FAIL (no full round)")
        return 1

    def rets(xs):
        return [v for x in xs for v in x["rets"]]

    def med(v):
        v = [x for x in v if x is not None]
        return st.median(v) if v else float("nan")

    c1 = [x for x in cur if x["slot"] == "C1"]
    c2 = [x for x in cur if x["slot"] == "C2"]
    noise = abs(med(rets(c1)) - med(rets(c2))) if c1 and c2 else noise_arg
    cr, nr = rets(cur), rets(new)
    print("rounds used:", rounds, " slots:", [f"{x['slot']}-r{x['r']}" for x in use])
    print("Home return  current %.1f ms (%d-%d, n=%d) | new %.1f ms (%d-%d, n=%d)"
          % (med(cr), min(cr), max(cr), len(cr), med(nr), min(nr), max(nr), len(nr)))
    if c1 and c2:
        print("noise        C1 %.1f vs C2 %.1f ms = %.1f ms" % (med(rets(c1)), med(rets(c2)), noise))
    elif noise is not None:
        print("noise        %.1f ms (given)" % noise)

    def col(xs, k):
        return [x[k] for x in xs]

    # cold start: the median of every cold start in the build's slots (one per slot, or AB_COLDS of them)
    cc = [v for x in cur for v in x["colds"]]
    nc = [v for x in new for v in x["colds"]]
    print("cold start   current %s ms (n=%d, %s-%s) | new %s ms (n=%d, %s-%s)"
          % (med(cc), len(cc), min(cc, default=None), max(cc, default=None),
             med(nc), len(nc), min(nc, default=None), max(nc, default=None)))
    print("janky        current %.2f%% | new %.2f%%" % (med(col(cur, "janky")), med(col(new, "janky"))))
    print("memory       current %.1f MB | new %.1f MB" % (med(col(cur, "pss")) / 1024, med(col(new, "pss")) / 1024))
    print("frame times  current p50/p90/p95/p99 %s/%s/%s/%s ms (%s frames) | new %s/%s/%s/%s ms (%s frames)"
          % tuple([med(col(cur, k)) for k in ("p50", "p90", "p95", "p99", "frames")]
                  + [med(col(new, k)) for k in ("p50", "p90", "p95", "p99", "frames")]))
    # a slot is healthy when the launcher is in front and the crash log has no launcher entries
    bad = [f"{x['slot']}-r{x['r']}: {' '.join(x['state'])}" for x in use
           if len(x["state"]) < 2 or not x["state"][1].startswith("com.gathin.tvhome/") or x["state"][-1] != "0"]
    d_ret = med(nr) - med(cr)
    allow = max(17.0, noise or 0.0)
    d_cold = med(nc) - med(cc)
    d_jank = med(col(new, "janky")) - med(col(cur, "janky"))
    d_mem = (med(col(new, "pss")) - med(col(cur, "pss"))) / 1024
    checks = [
        ("home return", d_ret <= allow and d_ret <= 30, "%+.1f ms (allowed %.1f, cap 30)" % (d_ret, allow)),
        ("cold start", d_cold <= 20, "%+.1f ms (allowed 20)" % d_cold),
        ("janky", d_jank <= 0.5, "%+.2f points (allowed 0.5)" % d_jank),
        ("memory", d_mem <= 3, "%+.1f MB (allowed 3)" % d_mem),
        ("no crash/freeze", not bad, "; ".join(bad) or "launcher in front, no crash log entries"),
    ]
    if noise is None:
        checks.append(("noise known", False, "a CN log needs --noise from the weekday test"))
    for name, ok, why in checks:
        print("  %-16s %s  %s" % (name, "PASS" if ok else "FAIL", why))
    passed = all(ok for _, ok, _ in checks)
    print("VERDICT: %s (%d full round%s)" % ("PASS" if passed else "FAIL", len(rounds), "" if len(rounds) == 1 else "s"))
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
