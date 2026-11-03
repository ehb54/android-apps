# Image for the word-list pipeline (tools/wordlists/words.py): spaCy with its
# German model, openpyxl for .xlsx files, Pillow for pictures. Built and run by
# tools/wordlists/run.sh; see tools/wordlists/README.md.
FROM python:3.12-slim

RUN pip install --no-cache-dir \
        spacy==3.8.18 \
        openpyxl==3.1.5 \
        pillow==13.0.0 \
        https://github.com/explosion/spacy-models/releases/download/de_core_news_md-3.8.0/de_core_news_md-3.8.0-py3-none-any.whl
