import argparse
import ctypes
import sys
import time


def main():
    parser = argparse.ArgumentParser(prog="rulik-server")
    parser.add_argument("--nogui", action="store_true", help="no window, console only")
    parser.add_argument("--dry", action="store_true", help="log input instead of sending it to Windows (for testing)")
    args = parser.parse_args()

    try:
        # чёткий текст на мониторах с масштабированием
        ctypes.windll.shcore.SetProcessDpiAwareness(1)
    except Exception:
        pass

    if args.dry:
        from . import gamepad, winput
        winput.dry_run()
        gamepad.dry_run()

    if args.nogui:
        from . import config, netinfo
        from .i18n import t
        from .server import Server
        cfg = config.load()
        config.save(cfg)
        server = Server(cfg)
        server.start()
        print(t("addresses"), ", ".join(netinfo.local_ips()), " PIN:", server.pin or t("pin_off"), flush=True)
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            server.stop()
        return

    from .gui import App
    App().run()


if __name__ == "__main__":
    sys.exit(main())
