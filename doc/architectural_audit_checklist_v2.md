# Architectural Audit & Verification: Part 2

Before we begin coding, this document outlines three final, high-impact architectural decisions regarding asset storage security, transaction boundaries, and Bangla script rendering.

---

## 1. Asset Storage Security (Public vs. Private Buckets)

### The Challenge:
Writers will upload book cover images, but also locked/paywalled PDF pages and chapter text fragments. If we upload all files to a single public MinIO bucket, users can bypass our billing system entirely by sharing direct MinIO URLs.

### The Decision:
We must establish two distinct buckets in MinIO:
1. **`pristha-public`**: Stores book covers, user avatars, and author bios. Accessible directly via public HTTP URLs.
2. **`pristha-private`**: Stores PDF pages and text drafts. This bucket has **zero public access**. 
   * To serve pages, the `reading` module must fetch the file programmatically from MinIO, apply the dynamic watermark in memory, and return the watermarked fragment to the reader.
   * Alternatively, we generate **Temporary Presigned URLs** with an expiration of **60 seconds**, ensuring links expire before they can be shared.

---

## 2. Transaction Boundaries Across Modules (Asynchronous Listeners)

### The Challenge:
When a reader purchases a chapter, the `billing` module deducts credits and inserts an `UnlockedContent` record. It also fires a `ContentUnlockedEvent` to update analytics read counters.

```
[Core Checkout]
  ├── Wallet Deduction (DB Write)
  ├── Grant Read Permission (DB Write)
  │
  └── [Event Published: ContentUnlockedEvent]
        ▼
   [Analytics Listener] ──> Update Read Counters (Failed) ──> ROLLBACK PAYMENT?
```

* **Synchronous Event Listener**: If the listener runs in the same transaction as the payment, a failure in `analytics` (e.g., database timeout updating counters) will **rollback the reader's checkout**. The reader is blocked from reading because a background counter failed to increment.
* **Asynchronous Event Listener**: The checkout transaction commits first. Then, the event is processed in the background.

### The Decision:
* **Rule**: All cross-module events must use Spring Modulith's **`@ApplicationModuleListener`** (which wraps `@TransactionalEventListener` and `@Async`). 
* If a background module (like `analytics` or `sms-notifications`) fails, it will **never** rollback our critical billing, checkout, or auth transactions.

---

## 3. Bangla Font Rendering & Layout (CTL Compliance)

### The Challenge:
Our business needs require dynamic watermarking and PDF generation. Rendering the Bangla script requires **Complex Text Layout (CTL)** processing. If we render text to a PDF or SVG canvas using standard Java fonts, the Bangla consonant conjuncts (যুক্তবর্ণ) will break (render as separate, incorrect characters).

### The Decision:
* We must bundle standard Bangla Unicode fonts (e.g., `SolaimanLipi.ttf` or `Kalpurush.ttf`) inside `src/main/resources/fonts/`.
* Our PDF generator (OpenHTMLtoPDF) and watermarking engines must explicitly load and register these TrueType fonts (.ttf) from resources and configure CTL (Complex Text Layout) support so that Bangla renders beautifully.
