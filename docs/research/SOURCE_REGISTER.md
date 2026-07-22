# Research Source Register

## Related registers

Physiology, terminology, and data-model sources live in the backend register:
`~/Projects/folicular/docs/research/SOURCES.md` (with topic notes linking each
source to the schema decision it informs). This register keeps product,
content, privacy, and platform sources.

## Use

Every external source must be tied to a specific product decision. A source is not permission to reproduce clinical language or add diagnosis features. Content derived from a source requires a review date, jurisdiction note, and plain-language scope check.

## Status definitions

- **Candidate:** identified but not yet reviewed for a product decision.
- **Reviewed:** content and publication context checked by the team.
- **Implemented:** a documented product decision cites the reviewed source.
- **Retired:** no longer current or appropriate.

## Initial candidates

| Area | Authority | Candidate source | Status | Intended decision |
|---|---|---|---|---|
| Menstrual health framing | World Health Organization | https://www.who.int/news/item/22-06-2022-who-statement-on-menstrual-health-and-rights | Candidate | Inclusive product terminology and scope |
| Premenstrual observations | NHS | https://www.nhs.uk/conditions/pre-menstrual-syndrome/ | Candidate | Observation vocabulary only, never diagnosis |
| Endometriosis context | World Health Organization | https://www.who.int/news-room/fact-sheets/detail/endometriosis | Candidate | Neutral terminology and safety review |
| Polycystic ovary syndrome context | World Health Organization | https://www.who.int/news-room/fact-sheets/detail/polycystic-ovary-syndrome | Candidate | Neutral terminology and irregular-cycle inclusion |
| Health-data classification | CNIL | https://www.cnil.fr/fr/sante | Candidate | Privacy classification and consent review (previous URL dead as of 2026-07-21; replaced by the CNIL sante hub) |
| Data protection design | CNIL | https://www.cnil.fr/fr/rgpd-de-quoi-parle-t-on | Candidate | French privacy baseline |
| Android accessibility | Android Developers | https://developer.android.com/guide/topics/ui/accessibility | Candidate | Compose semantics and test plan |
| Android backup | Android Developers | https://developer.android.com/identity/data/autobackup | Candidate | Sensitive-data backup policy |

## Required follow-up

- Verify each URL, publication date, and update status before citing it in user-facing content.
- Add authoritative French public-health sources where they cover the exact decision.
- Record source excerpts only when licensing and context permit.
- Schedule periodic review for any educational content shipped in the app.
- Obtain appropriate domain review before publishing content that could be understood as health guidance.
