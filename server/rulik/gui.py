import queue
import sys
import threading
import time
from pathlib import Path
import tkinter as tk
from tkinter import messagebox

from . import __version__, config, gamepad, netinfo
from .i18n import t
from .server import Server

BG = "#0e1014"
CARD = "#171a21"
FG = "#e9ecf2"
DIM = "#8a93a6"
ACCENT = "#ff5c38"
OK = "#3ddc84"
FONT = "Segoe UI"


class App:
    def __init__(self):
        self.cfg = config.load()
        config.save(self.cfg)
        self.events = queue.Queue()
        self.server = Server(self.cfg, on_change=lambda: self.events.put(("change", None)),
                             on_log=lambda m: self.events.put(("log", m)))

        self.root = tk.Tk()
        self.root.title(f"Rulik Server {__version__}")
        self.root.configure(bg=BG)
        self.root.geometry("460x640")
        self.root.minsize(420, 560)
        self.root.protocol("WM_DELETE_WINDOW", self.quit)
        base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent))
        try:
            self.root.iconbitmap(str(base / "rulik.ico"))
        except tk.TclError:
            pass
        self._build()

        try:
            self.server.start()
        except OSError as e:
            messagebox.showerror("Rulik", t("port_busy", port=self.server.port) + f"\n\n{e}")
            self.root.destroy()
            raise SystemExit(1)
        self.nets = [{"ip": ip} for ip in netinfo.local_ips()]
        self.fw_rule = True
        self.refresh()
        self._watch_networks()
        self.root.after(100, self._pump)
        self.root.after(1000, self._tick)

    # --- разметка ---

    def _card(self, title):
        frame = tk.Frame(self.root, bg=CARD, padx=14, pady=10)
        frame.pack(fill="x", padx=14, pady=(0, 10))
        tk.Label(frame, text=title.upper(), bg=CARD, fg=DIM, font=(FONT, 8, "bold")).pack(anchor="w")
        return frame

    def _button(self, parent, text, cmd, accent=False):
        return tk.Button(parent, text=text, command=cmd, relief="flat", cursor="hand2",
                         bg=ACCENT if accent else "#252a35", fg="#ffffff", activebackground="#ff7a5c",
                         activeforeground="#ffffff", font=(FONT, 9, "bold"), padx=10, pady=4, bd=0)

    def _build(self):
        top = tk.Frame(self.root, bg=BG, padx=14, pady=12)
        top.pack(fill="x")
        tk.Label(top, text="RULIK", bg=BG, fg=ACCENT, font=(FONT, 20, "bold")).pack(side="left")
        tk.Label(top, text="  " + t("tagline"), bg=BG, fg=DIM, font=(FONT, 10)).pack(side="left", pady=(8, 0))

        c = self._card(t("address"))
        self.ip_label = tk.Label(c, bg=CARD, fg=FG, font=(FONT, 12, "bold"), justify="left")
        self.ip_label.pack(anchor="w")
        tk.Label(c, text=t("address_hint"), bg=CARD, fg=DIM,
                 font=(FONT, 9)).pack(anchor="w")
        self.net_warn = tk.Label(c, bg=CARD, fg="#ffb347", font=(FONT, 9, "bold"), justify="left", wraplength=400)

        c = self._card(t("pin"))
        row = tk.Frame(c, bg=CARD)
        row.pack(fill="x")
        self.pin_label = tk.Label(row, bg=CARD, fg=FG, font=("Consolas", 28, "bold"))
        self.pin_label.pack(side="left")
        self._button(row, t("new_pin"), self.new_pin).pack(side="right")
        self.require_pin = tk.BooleanVar(value=self.cfg.get("require_pin", True))
        tk.Checkbutton(c, text=t("ask_pin"), variable=self.require_pin, command=self.toggle_pin,
                       bg=CARD, fg=FG, selectcolor=CARD, activebackground=CARD, activeforeground=FG,
                       font=(FONT, 9)).pack(anchor="w")

        c = self._card(t("pad"))
        row = tk.Frame(c, bg=CARD)
        row.pack(fill="x")
        self.pad_label = tk.Label(row, bg=CARD, fg=FG, font=(FONT, 10), justify="left", wraplength=280)
        self.pad_label.pack(side="left")
        self.driver_btn = self._button(row, t("install_driver"), self.install_driver, accent=True)

        c = self._card(t("devices"))
        self.devices = tk.Label(c, bg=CARD, fg=FG, font=(FONT, 10), justify="left", anchor="w")
        self.devices.pack(fill="x")

        row = tk.Frame(self.root, bg=BG, padx=14)
        row.pack(fill="x", pady=(0, 10))
        self._button(row, t("usb"), self.usb).pack(side="left")
        self._button(row, t("firewall"), self.firewall).pack(side="left", padx=6)
        self._button(row, t("kick"), self.server.kick_all).pack(side="left")

        self.log = tk.Text(self.root, height=6, bg=CARD, fg=DIM, relief="flat", font=("Consolas", 9),
                           padx=10, pady=6, state="disabled")
        self.log.pack(fill="both", expand=True, padx=14, pady=(0, 14))

    # --- обновление ---

    def refresh(self):
        rows = []
        for n in self.nets:
            row = f"{n['ip']}:{self.server.port}"
            if n.get("alias"):
                row += f"   {n['alias']}" + (f" · {t('public_tag')}" if n.get("public") else "")
            rows.append(row)
        self.ip_label.config(text="\n".join(rows))
        public = [n for n in self.nets if n.get("public")]
        if public and not self.fw_rule:
            self.net_warn.config(text=t("public_warn", name=public[0].get("alias", "")))
            self.net_warn.pack(anchor="w", pady=(6, 0))
        else:
            self.net_warn.pack_forget()
        self.pin_label.config(text=self.cfg["pin"] if self.require_pin.get() else "— — — —")
        if gamepad.available():
            self.pad_label.config(text=t("pad_ok"), fg=OK)
            self.driver_btn.pack_forget()
        else:
            self.pad_label.config(text=t("pad_missing"),
                                  fg="#ffb347")
            self.driver_btn.pack(side="right")
        lines = []
        for s in self.server.clients():
            via = t("via_net") if s.addr else "USB (adb)"
            parts = [f"● {s.name}", via]
            if s.rtt_ms:
                parts.append(f"{s.rtt_ms} {t('ms')}")
            if s.pad:
                parts.append(t("gamepad"))
            if s.idle:
                parts.append(t("lost"))
            lines.append("  ·  ".join(parts))
        self.devices.config(text="\n".join(lines) or t("nobody"),
                            fg=FG if lines else DIM)

    def write_log(self, msg):
        self.log.config(state="normal")
        self.log.insert("end", time.strftime("%H:%M:%S ") + msg + "\n")
        self.log.see("end")
        self.log.config(state="disabled")

    def _pump(self):
        changed = False
        try:
            while True:
                kind, payload = self.events.get_nowait()
                if kind == "log":
                    self.write_log(payload)
                elif kind == "nets":
                    self.nets, self.fw_rule = payload
                changed = True
        except queue.Empty:
            pass
        if changed:
            self.refresh()
        self.root.after(100, self._pump)

    def _watch_networks(self):
        """Раз в несколько секунд перечитываем сети: USB-модем появляется и пропадает на ходу."""
        def work():
            self.events.put(("nets", (netinfo.networks(), netinfo.has_port_rule())))
        threading.Thread(target=work, daemon=True).start()
        self.root.after(4000, self._watch_networks)

    def _tick(self):
        self.refresh()
        self.root.after(2000, self._tick)

    # --- действия ---

    def new_pin(self):
        self.cfg["pin"] = config.new_pin()
        config.save(self.cfg)
        self.refresh()

    def toggle_pin(self):
        self.cfg["require_pin"] = self.require_pin.get()
        config.save(self.cfg)
        self.refresh()

    def install_driver(self):
        if gamepad.install_driver():
            messagebox.showinfo("Rulik", t("driver_started"))
        else:
            import webbrowser
            webbrowser.open("https://github.com/nefarius/ViGEmBus/releases/latest")

    def usb(self):
        ok, msg = netinfo.adb_reverse(self.server.port)
        self.write_log(msg)
        if not ok:
            messagebox.showwarning("Rulik", msg)

    def firewall(self):
        if netinfo.open_firewall(self.server.port):
            self.write_log(t("fw_added"))

    def quit(self):
        self.server.stop()
        self.root.destroy()

    def run(self):
        self.root.mainloop()
