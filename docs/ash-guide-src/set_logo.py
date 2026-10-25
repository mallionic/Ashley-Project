#!/usr/bin/env python3
"""
Replace the logo of the guide and rebuild the PDF.

    python3 set_logo.py path/to/new_logo.png        (PNG or JPG)
    python3 set_logo.py --remove                    (use the text mark)
"""
import os
import shutil
import subprocess
import sys

import config


def main():
    if len(sys.argv) != 2:
        print(__doc__.strip())
        return 1
    os.makedirs(os.path.dirname(config.LOGO), exist_ok=True)
    if sys.argv[1] == '--remove':
        if os.path.exists(config.LOGO):
            os.remove(config.LOGO)
        print('logo removed: the text mark will be used')
    else:
        src = sys.argv[1]
        if not os.path.isfile(src):
            print('no such file: ' + src)
            return 1
        shutil.copyfile(src, config.LOGO)
        print('logo set: ' + config.LOGO)
    builder = os.path.join(config.HERE, 'build_guide.py')
    return subprocess.call([sys.executable, builder])


if __name__ == '__main__':
    sys.exit(main())
