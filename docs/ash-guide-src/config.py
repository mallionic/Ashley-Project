"""
Settings of the ASH Programming Guide. Edit and run build_guide.py again.
"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))

# Logo shown on the cover (top right) and in every page header.
# Replace the file (or run:  python3 set_logo.py my_logo.png ) and rebuild.
# When the file does not exist a plain "ASH" text mark is drawn instead.
LOGO = os.path.join(HERE, 'assets', 'logo.png')

TITLE_LINES = ['The Ashley Project', 'ASH Programming Language']
COVER_LABEL = 'Programming Guide and Reference Manual'
SUBTITLE = 'Language, compiler (ashc), ASHIR, shasm assembler and the nlib library'
DOC_NUMBER = 'ASH-0001-EN'
REVISION = '1.0'
DATE = 'October 2026'
COPYRIGHT = 'Copyright © 2026 Mallionic Inc. All rights reserved.'
RUNNING_TITLE = 'ASH PROGRAMMING GUIDE AND REFERENCE MANUAL'

# Main accent colour (titles, rules) and secondary colour.
ACCENT = '#A3261B'
ACCENT_DARK = '#6E1A12'
INK = '#1E2228'
MUTED = '#5B616B'

# Path of the compiler used to verify and run every example.
PROJECT = os.path.dirname(os.path.dirname(HERE))
ASHC = os.environ.get('ASHC', os.path.join(PROJECT, 'out', 'ashc'))
OUTPUT = os.path.join(PROJECT, 'docs', 'ash-guide.pdf')
JAR = os.environ.get('ASHC_JAR', os.path.join(PROJECT, 'out', 'ashc-1.0.0.jar'))
