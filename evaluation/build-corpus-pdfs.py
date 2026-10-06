"""Build the project-owned DOCSEARCH-08 benchmark PDFs from page sources."""

from pathlib import Path

from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer


ROOT = Path(__file__).parent / "benchmark-v1"
SOURCE_DIR = ROOT / "corpus-sources"
PDF_DIR = ROOT / "corpus"


def page_footer(canvas, document):
    canvas.saveState()
    canvas.setFont("Helvetica", 8)
    canvas.setFillColorRGB(0.35, 0.35, 0.35)
    canvas.drawCentredString(A4[0] / 2, 12 * mm, f"DOCSEARCH-08 project-owned benchmark | page {document.page}")
    canvas.restoreState()


def build(source_path: Path):
    text = source_path.read_text(encoding="utf-8")
    preamble, _ = text.split("=== PAGE 1 ===", 1)
    title = next(line.removeprefix("TITLE: ") for line in preamble.splitlines() if line.startswith("TITLE:"))
    author = next(line.removeprefix("AUTHOR: ") for line in preamble.splitlines() if line.startswith("AUTHOR:"))
    sections = text.split("=== PAGE ")
    pages = []
    for section in sections[1:]:
        _, body = section.split(" ===\n", 1)
        pages.append(body.strip())
    if len(pages) != 3:
        raise ValueError(f"{source_path} must contain exactly three pages")

    output = PDF_DIR / f"{source_path.stem}.pdf"
    document = SimpleDocTemplate(
        str(output),
        pagesize=A4,
        rightMargin=22 * mm,
        leftMargin=22 * mm,
        topMargin=22 * mm,
        bottomMargin=22 * mm,
        title=source_path.stem,
        author="DocSearch AI benchmark team",
    )
    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        "BenchmarkTitle",
        parent=styles["Title"],
        alignment=TA_CENTER,
        fontName="Helvetica-Bold",
        fontSize=17,
        leading=22,
        spaceAfter=18,
    )
    body_style = ParagraphStyle(
        "BenchmarkBody",
        parent=styles["BodyText"],
        fontName="Helvetica",
        fontSize=12,
        leading=18,
        spaceAfter=12,
    )
    story = []
    for index, page in enumerate(pages, start=1):
        lines = page.splitlines()
        body = " ".join(line.strip() for line in lines if line.strip())
        story.extend(
            [
                Paragraph(title, title_style),
                Paragraph(f"Source page {index} | {author}", styles["Normal"]),
                Spacer(1, 16),
                Paragraph(body, body_style),
            ]
        )
        if index != len(pages):
            story.append(PageBreak())
    document.build(story, onFirstPage=page_footer, onLaterPages=page_footer)
    print(output)


def main():
    PDF_DIR.mkdir(parents=True, exist_ok=True)
    for source_path in sorted(SOURCE_DIR.glob("*.txt")):
        build(source_path)


if __name__ == "__main__":
    main()
