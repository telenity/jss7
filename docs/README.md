# jSS7 Documentation

Each chapter is a GitHub-flavoured Markdown file that renders directly on GitHub. The chapters are also bound into two PDF books.

| Book | Chapters |
|------|----------|
| **JSS7_User_Guide.pdf** | [Chapter-Architecture.md](Chapter-Architecture.md), [Chapter-M3UA.md](Chapter-M3UA.md), [Chapter-SCCP.md](Chapter-SCCP.md), [Chapter-TCAP.md](Chapter-TCAP.md) |
| **JSS7_Shell_Guide.pdf** | [Chapter-Shell.md](Chapter-Shell.md) |

| Chapter | Content |
|---------|---------|
| [Chapter-Architecture.md](Chapter-Architecture.md) | Layers and modules, wiring the stack in Java, management, persistence |
| [Chapter-M3UA.md](Chapter-M3UA.md) | Roles, AS/ASP, routes, load sharing, state machines, heartbeat |
| [Chapter-SCCP.md](Chapter-SCCP.md) | Global title translation, rule types, worked examples, SCCP user API |
| [Chapter-TCAP.md](Chapter-TCAP.md) | Dialogs, components, timers, client example |
| [Chapter-Shell.md](Chapter-Shell.md) | Every `sctp`, `m3ua` and `sccp` shell command with arguments and examples |

Images live in `images/`; the `.odg` files next to them are the editable sources.

## Building the PDFs

PDF rendering is a Maven module that is only activated by the `docs` profile, so the normal build is unaffected:

```
mvn -Pdocs -pl docs package
```

The books are written to `docs/target/pdf/`. Which chapters go into which book is defined in `build/books.properties`; adding a chapter means adding its file name to a line there. The renderer (`build/src/main/java/.../PdfBuilder.java`) uses flexmark for Markdown and openhtmltopdf for the PDF, both pure Java, so no pandoc, LaTeX or browser is needed. Styling is in `build/pdf.css`: a cover page per book, every chapter and every `## ` section on a new page, coloured headings, GitHub alert boxes, striped tables, shell commands in bold blue with responses in grey italic, and page numbers.

Writing conventions used across the chapters:

- A `## Contents` list of `[Title](#anchor)` links near the top; anchors are the GitHub slug of the heading.
- Links to another chapter are written as `[Chapter-X.md](Chapter-X.md#anchor)`; inside a book they become internal PDF links.
- `> [!NOTE]`, `> [!TIP]`, `> [!WARNING]`, `> [!IMPORTANT]` for callouts.
- `**Optional**`, `**Mandatory**`, `**default**` in bold; `*Case-sensitive*` and similar notes in italic.
- Shell examples show the command followed by the exact response text the code returns.
