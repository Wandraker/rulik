"""Строки приложения на двух языках в одном месте.

Запуск: python android/tools/strings.py
Пишет app/src/main/res/values/strings.xml (английский) и values-ru/strings.xml (русский).
"""

from pathlib import Path

S = {
    "app_name": ("Rulik", None),
    "tagline": ("control your PC from your phone", "рули своим ПК с телефона"),
    "tab_connect": ("Connect", "Подключение"),
    "layouts": ("Layouts", "Раскладки"),
    "st_idle": ("Not connected", "Не подключено"),
    "st_connecting": ("Connecting to %1$s…", "Подключаюсь к %1$s…"),
    "st_connected": ("Connected: %1$s", "Подключено: %1$s"),
    "st_bad_pin": ("Wrong PIN", "Неверный PIN"),
    "st_need_pin": ("Enter the PIN", "Введите PIN"),
    "st_full": ("Too many devices on this PC", "К этому ПК подключено слишком много устройств"),
    "st_timeout": ("PC is not responding. Is Rulik Server running?", "ПК не отвечает. Rulik Server запущен?"),
    "st_network": ("Connection lost", "Связь потеряна"),
    "no_link": ("no link", "нет связи"),
    "disconnect": ("Disconnect", "Отключить"),
    "no_driver": ("The gamepad driver (ViGEmBus) is not installed on the PC, so only mouse and keyboard work. "
                  "Install it from the Rulik Server window.",
                  "На ПК не стоит драйвер геймпада (ViGEmBus), поэтому работают только мышь и клавиатура. "
                  "Поставьте его из окна Rulik Server."),
    "pcs_found": ("PCs on the network", "ПК в сети"),
    "refresh": ("Search again", "Искать снова"),
    "nothing_found": ("Nothing found. Start Rulik Server on the PC and make sure the phone is on the same network. "
                      "If the firewall blocks it, press \"Firewall\" in the server window.",
                      "Ничего не найдено. Запустите Rulik Server на ПК и проверьте, что телефон в той же сети. "
                      "Если мешает брандмауэр, нажмите «Брандмауэр» в окне сервера."),
    "manual": ("Enter the address manually", "Ввести адрес вручную"),
    "connect": ("Connect", "Подключить"),
    "usb_title": ("Cable connection", "Подключение по кабелю"),
    "usb_steps": ("1. Turn on USB debugging on the phone (Developer options).\n"
                  "2. Connect the phone to the PC with a cable.\n"
                  "3. Press \"USB (adb)\" in the Rulik Server window.\n"
                  "4. Press the button below.",
                  "1. Включите отладку по USB на телефоне (раздел «Для разработчиков»).\n"
                  "2. Подключите телефон к ПК кабелем.\n"
                  "3. Нажмите «USB (adb)» в окне Rulik Server.\n"
                  "4. Нажмите кнопку ниже."),
    "connect_usb": ("Connect via USB", "Подключить по USB"),
    "usb_tether": ("Without debugging: turn on \"USB tethering\" on the phone and use the Wi-Fi tab. "
                   "The PC will show up in the list and the traffic goes over the cable.",
                   "Без отладки: включите на телефоне «USB-модем» и используйте вкладку Wi-Fi. "
                   "ПК появится в списке, а связь пойдёт по кабелю."),
    "settings": ("Settings", "Настройки"),
    "device_name": ("Phone name on the PC", "Имя телефона на ПК"),
    "show_ping": ("Show ping while playing", "Показывать пинг в игре"),
    "open_site": ("Website and instructions", "Сайт и инструкции"),
    "pin_title": ("PIN code", "PIN-код"),
    "pin_text": ("Enter the PIN shown in the Rulik Server window on %1$s", "Введите PIN из окна Rulik Server на %1$s"),
    "cancel": ("Cancel", "Отмена"),
    "done": ("Done", "Готово"),
    "save": ("Save", "Сохранить"),
    "close": ("Close", "Закрыть"),
    "discard": ("Don't save", "Не сохранять"),
    "unsaved_title": ("Save changes?", "Сохранить изменения?"),
    "unsaved_text": ("The layout has changed.", "Раскладка изменилась."),
    "play": ("Play", "Играть"),
    "edit_layout": ("Edit", "Редактировать"),
    "duplicate": ("Duplicate", "Дублировать"),
    "share": ("Share", "Поделиться"),
    "copy_json": ("Copy as text", "Копировать текстом"),
    "copied": ("Copied", "Скопировано"),
    "delete": ("Delete", "Удалить"),
    "delete_q": ("Delete \"%1$s\"?", "Удалить «%1$s»?"),
    "copy_suffix": ("(copy)", "(копия)"),
    "import_clip": ("Import from clipboard", "Импорт из буфера"),
    "imported": ("Layout added", "Раскладка добавлена"),
    "bad_import": ("No layout in the clipboard", "В буфере нет раскладки"),
    "restore_presets": ("Restore built-in layouts", "Вернуть стандартные раскладки"),
    "new_layout": ("New layout", "Новая раскладка"),
    "more_layouts": ("More layouts on the website", "Ещё раскладки на сайте"),
    "n_controls": ("%1$d controls", "элементов: %1$d"),
    "keyboard": ("Keyboard", "Клавиатура"),
    "exit": ("Exit", "Выйти"),
    "type_on_pc": ("Typing on the PC", "Печать на ПК"),
    "type_hint": ("Letters are sent right away", "Буквы уходят сразу"),
    "add": ("Add", "Добавить"),
    "properties": ("Properties", "Свойства"),
    "layout_settings": ("Layout settings", "Настройки раскладки"),
    "editor_empty": ("Empty for now. Press + to add a button, stick, D-pad or touchpad. "
                     "Drag to move, pull the corner circle to resize.",
                     "Пока пусто. Нажмите +, чтобы добавить кнопку, стик, крестовину или тачпад. "
                     "Перетаскивайте пальцем, размер меняйте за кружок в углу."),
    "t_button": ("Button", "Кнопка"),
    "t_stick": ("Stick", "Стик"),
    "t_dpad": ("D-pad", "Крестовина"),
    "t_touchpad": ("Touchpad", "Тачпад"),
    "label": ("Label", "Надпись"),
    "action": ("Action", "Действие"),
    "mode": ("Mode", "Режим"),
    "m_hold": ("Hold", "Удержание"),
    "m_toggle": ("Toggle", "Переключатель"),
    "m_turbo": ("Turbo", "Турбо"),
    "turbo_speed": ("Turbo speed", "Скорость турбо"),
    "shape": ("Shape", "Форма"),
    "s_circle": ("Circle", "Круг"),
    "s_rect": ("Rectangle", "Прямоугольник"),
    "output": ("Output", "Что делает"),
    "o_left": ("Left stick", "Левый стик"),
    "o_right": ("Right stick", "Правый стик"),
    "o_arrows": ("Arrows", "Стрелки"),
    "o_mouse": ("Mouse", "Мышь"),
    "o_dpad": ("D-pad", "Крестовина"),
    "o_camera": ("Camera (right stick)", "Камера (правый стик)"),
    "floating": ("Floating (centers where you touch)", "Плавающий (центр там, где коснулись)"),
    "deadzone": ("Dead zone", "Мёртвая зона"),
    "sensitivity": ("Sensitivity", "Чувствительность"),
    "touchpad_hint": ("Tap: left click. Two-finger tap: right click. Two fingers: scroll. Tap then hold: drag.",
                      "Тап: левый клик. Тап двумя пальцами: правый клик. Двумя пальцами: прокрутка. "
                      "Тап и удержание: перетаскивание."),
    "camera_hint": ("Swipe to turn the camera like with a stick. Works in any gamepad game.",
                    "Свайп крутит камеру как стиком. Работает в любой игре на геймпаде."),
    "size": ("Size", "Размер"),
    "width": ("Width", "Ширина"),
    "height": ("Height", "Высота"),
    "tab_pad": ("Gamepad", "Геймпад"),
    "tab_keys": ("Keyboard", "Клавиатура"),
    "tab_mouse": ("Mouse", "Мышь"),
    "tab_special": ("Special", "Особые"),
    "combo_hint": ("Pick up to 4 keys for a combo. Now: %1$s", "Можно выбрать до 4 клавиш для сочетания. Сейчас: %1$s"),
    "wheel_up": ("Wheel up", "Колесо вверх"),
    "wheel_down": ("Wheel down", "Колесо вниз"),
    "a_gyro": ("Gyro while held", "Гироскоп, пока зажато"),
    "a_text": ("Open the keyboard", "Открыть клавиатуру"),
    "a_none": ("Nothing", "Ничего"),
    "name": ("Name", "Название"),
    "portrait": ("Portrait", "Портрет"),
    "haptics": ("Vibrate on press", "Вибрация при нажатии"),
    "opacity": ("Opacity", "Непрозрачность"),
    "gyro": ("Gyro", "Гироскоп"),
    "off": ("Off", "Выкл"),
    "gyro_always": ("Always on", "Всегда включён"),
    "gyro_hold_hint": ("Add a button with the \"Gyro while held\" action (Special tab).",
                       "Добавьте кнопку с действием «Гироскоп, пока зажато» (вкладка «Особые»)."),
    "invert_x": ("Invert horizontal", "Инвертировать по горизонтали"),
    "invert_y": ("Invert vertical", "Инвертировать по вертикали"),
    "tilt": ("Tilt", "Наклон"),
    "tilt_on": ("Steering wheel by tilting (left stick)", "Руль наклоном (левый стик)"),
    "tilt_angle": ("Full turn angle", "Угол полного поворота"),
    "invert": ("Invert", "Инвертировать"),
}


def esc(s: str) -> str:
    return (s.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")
            .replace('"', '\\"').replace("\n", "\\n"))


def main():
    res = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
    for folder, i in (("values", 0), ("values-ru", 1)):
        lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
        for key, pair in S.items():
            if pair[i] is not None:
                lines.append(f'    <string name="{key}">{esc(pair[i])}</string>')
        lines.append("</resources>\n")
        (res / folder).mkdir(parents=True, exist_ok=True)
        (res / folder / "strings.xml").write_text("\n".join(lines), encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
