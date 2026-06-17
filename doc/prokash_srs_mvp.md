# Software Requirements Specification (SRS) - Prokash Digital MVP

This document specifies the comprehensive functional and non-functional requirements for the first MVP of **Prokash Digital**, focusing on the individual writer ecosystem, flexible social publishing, and micro-payment chapter unlocks.

---

## 1. User Roles & Permissions

The system must support the following roles:
* **Guest (Unauthenticated)**: Can browse the public catalog, search for books/posts, and read free preview pages.
* **Reader**: A registered user who can browse, follow authors, add items to a personal library, load wallet credits, and purchase/unlock locked chapters or stand-alone posts.
* **Author**: A Reader who has activated an Author Profile. Can write books/posts, publish chapters, configure pricing, view transaction ledgers, and manage payouts.
* **B2B Publisher Representative**: A user associated with a publishing house tenant who can browse the scouting dashboard, analyze readership statistics, and send print licensing contract offers to authors.
* **Moderator (Admin)**: An administrator who handles copyright claims, evaluates reader payout disputes, and manages general platform settings.

---

## 2. Comprehensive Bounded Module Functional Requirements

```
                                PROKASH DIGITAL MVP
┌──────────────────────────────────────────────────────────────────────────────┐
│  IDENTITY MODULE                                                             │
│  - ID-FR-01: Phone Registration    - ID-FR-06: Author Profile Activation     │
│  - ID-FR-02: Phone OTP (Mock)      - ID-FR-07: Reader Profile Update         │
│  - ID-FR-03: Login & JWT Session   - ID-FR-08: Password Reset Flow           │
│  - ID-FR-04: Token Refresh Flow    - ID-FR-09: New-Device Takeover Alert     │
│  - ID-FR-05: Logout (Revocation)   - ID-FR-10: Concurrent Device Check       │
├──────────────────────────────────────────────────────────────────────────────┤
│  STUDIO MODULE (Authoring Workspace)                                         │
│  - STUDIO-FR-01: Standalone Post Cr. - STUDIO-FR-08: Rich-Media Ingestion    │
│  - STUDIO-FR-02: Post Edit / Delete  - STUDIO-FR-09: Publish Content Event   │
│  - STUDIO-FR-03: Book Statuses       - STUDIO-FR-10: Reviewer Invitations    │
│  - STUDIO-FR-04: Book Update / Del.  - STUDIO-FR-11: Reviewer Comments       │
│  - STUDIO-FR-05: Chapter Creation    - STUDIO-FR-12: IP Takedown & Disputes  │
│  - STUDIO-FR-06: Chapter Re-ordering - STUDIO-FR-13: B2B Rights Exchange     │
│  - STUDIO-FR-07: Chapter Edit / Del.                                         │
├──────────────────────────────────────────────────────────────────────────────┤
│  CATALOG MODULE (Public Discovery)                                           │
│  - CAT-FR-01: Social Feed API      - CAT-FR-04: Flexible Categorization      │
│  - CAT-FR-02: Public Search API    - CAT-FR-05: Viral Quote Generator        │
│  - CAT-FR-03: Book Read Preview API- CAT-FR-06: Abuse Flag & Report         │
├──────────────────────────────────────────────────────────────────────────────┤
│  READING MODULE                                                              │
│  - READ-FR-01: Secure Page Server  - READ-FR-04: Reader Library Shelf        │
│  - READ-FR-02: Access Verification - READ-FR-05: Reading Progress Save       │
│  - READ-FR-03: Pixel Watermarking  - READ-FR-06: Reading Progress Resume     │
├──────────────────────────────────────────────────────────────────────────────┤
│  BILLING MODULE                                                              │
│  - BILL-FR-01: Wallet Management   - BILL-FR-08: Dispute Settlement          │
│  - BILL-FR-02: SSLCommerz Mock     - BILL-FR-09: Promo & Referrals           │
│  - BILL-FR-03: One-Tap Unlock      - BILL-FR-10: Affiliate Split             │
│  - BILL-FR-04: Immutable Ledger    - BILL-FR-11: Dynamic Commission          │
│  - BILL-FR-05: Author Earnings     - BILL-FR-12: Escrow Settlement           │
│  - BILL-FR-06: Author Withdrawal   - BILL-FR-13: VAT & Invoice Engine        │
│  - BILL-FR-07: Daily Ledger Reconc.- BILL-FR-14: Instant TxnID Lookup        │
├──────────────────────────────────────────────────────────────────────────────┤
│  ANALYTICS MODULE                                                            │
│  - AN-FR-01: Read Engagement       - AN-FR-04: Scouting Score Engine         │
│  - AN-FR-02: Chapter Completion    - AN-FR-05: Cohort Retention              │
│  - AN-FR-03: Author Dashboard Stats                                          │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

### A. Identity Module (`identity`)

#### ID-FR-01: Phone Registration
* **Input**: Phone Number (Bangladeshi format), Full Name, Password, Confirm Password.
* **Validation**:
  - Phone number must match the format: `^(?:\+8801|8801|01)[3-9]\d{8}$`.
  - Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character.
  - Password and Confirm Password must match exactly.
* **Processing**:
  - The system must verify that the phone number is not already registered.
  - The password must be hashed using BCrypt.
  - Create a new User record with status `PENDING_VERIFICATION`.

#### ID-FR-02: Phone OTP Verification (Mock Flow)
* **Input**: Phone Number, 6-digit OTP Code.
* **Validation**:
  - Verify that the phone number exists and has a status of `PENDING_VERIFICATION`.
  - The OTP code must match the unexpired code stored in Redis (5-minute TTL).
* **Processing**:
  - Transition the user status to `VERIFIED`.
  - Create a default `ReaderProfile` linked to the user.
  - Delete the OTP key from Redis upon successful verification.

#### ID-FR-03: Login & Session Generation
* **Input**: Phone Number, Password, Device Fingerprint.
* **Validation**:
  - Check if the phone number exists and status is `VERIFIED`.
  - Match the password hash using BCrypt validation.
* **Processing**:
  - Generate a signed RS256 JWT Access Token (15-minute expiration) containing claims: `userId`, `roles`, `activeProfileId`, `tenantId`.
  - Generate a random UUID Refresh Token (30-day expiration) and save it in Redis keyed by user ID.
  - Return tokens and profiles list.

#### ID-FR-04: Token Refresh Flow
* **Input**: Expired Access Token, Active Refresh Token.
* **Validation**:
  - Validate the signature of the expired access token.
  - Verify the refresh token is present in Redis and is not expired.
* **Processing**:
  - Revoke the old refresh token.
  - Generate a new signed JWT Access Token and a new rotating Refresh Token.
  - Save the new refresh token in Redis.

#### ID-FR-05: Logout (Session Revocation)
* **Input**: User Session Token.
* **Processing**:
  - Delete the refresh token from Redis.
  - Blacklist the access token in Redis for the remainder of its TTL.

#### ID-FR-06: Author Profile Activation
* **Input**: User ID, Pen Name, Biography, Mobile Financial Service (MFS) Payout Details (bKash/Nagad number).
* **Validation**:
  - Pen Name must be unique (check against active author profiles).
  - Payout details must be valid phone numbers.
* **Processing**:
  - Create an `AuthorProfile` linked to the user.
  - Update user roles to include `AUTHOR`.
  - Initialize the Author's tenant settings (setting the writer's personal tenant context).

#### ID-FR-07: Reader Profile Update
* **Input**: User ID, Full Name, Avatar Image, Bio, Email.
* **Validation**:
  - Email format must be valid (if provided).
  - Avatar file type must be JPEG or PNG.
* **Processing**:
  - Update the user's reader profile details.

#### ID-FR-08: Password Reset Flow
* **Input**: Phone Number, OTP, New Password.
* **Validation**:
  - Validate OTP matches code in Redis.
* **Processing**:
  - Hash the new password and update the User record.

#### ID-FR-09: New-Device Account Takeover Alert
* **Input**: Device Fingerprint during login.
* **Processing**:
  - Compare the login device fingerprint against the user's previously recorded devices.
  - If a new fingerprint is detected, trigger a warning email/SMS alert and flag the session.

#### ID-FR-10: Concurrent Device Session Enforcement (DRM Guardrail)
* **Input**: Device Fingerprint, User ID during active session handshake.
* **Validation**:
  - Retrieve active refresh token counts in Redis.
* **Processing**:
  - Enforce a maximum limit of **2 active device sessions** per user account.
  - If a 3rd device logs in, automatically invalidate the oldest refresh token and force log-out that session, preventing widespread password/account sharing.

---

### B. Catalog Module (`catalog`)

#### CAT-FR-01: Social Feed API
* **Input**: Reader ID, Pageable.
* **Processing**:
  - Query followed authors list for the reader.
  - Retrieve and return a chronologically sorted page list of published Standalone Posts and published Chapters.

#### CAT-FR-02: Public Search API
* **Input**: Query String (Bangla/English), Filters (Tags, Author), Pageable.
* **Processing**:
  - Query Elasticsearch index using fuzzy matching.
  - Return matching public books, chapters, standalone posts, and authors.

#### CAT-FR-03: Book Details / Read Preview API
* **Input**: Book ID, Guest/Reader ID.
* **Processing**:
  - Return book details, author pen name, genre tags, and the list of published chapters indicating whether they are Free or Locked.

#### CAT-FR-04: Flexible Categorization
* **Input**: Content ID (Book/Post), List of Tag IDs.
* **Processing**:
  - Link the target content to the specified tags (many-to-many relationship).
  - Writers must be able to assign no tags initially, and add or remove tags at any time in the future.

#### CAT-FR-05: Viral Quote Image Generator (Growth Loop)
* **Input**: User ID, Selected Content Block ID, Highlighted Text Range, Design Template Choice.
* **Processing**:
  - Generate an open-graph compatible image overlay card containing the selected quote, author pen name, book cover, and a secure QR code referencing the content's unlock path.
  - Return the image payload for direct sharing to Facebook feeds.

#### CAT-FR-06: Content Flagging and Abuse Reporting
* **Input**: User ID, Target Content ID, Abuse Category (Plagiarism, Adult, Hate Speech), Description.
* **Processing**:
  - Log an `AbuseReport` entry.
  - If the content receives $\ge 5$ flags within a 24-hour period, automatically flag it as `UNDER_REVIEW` and notify the moderation queue, preventing toxic content from distributing.

---

### C. Author Studio Module (`studio`)

#### STUDIO-FR-01: Stand-alone Post Creation (Social Writing)
* **Input**: Author ID, Title (Optional), Body (Rich-text JSON/HTML format), Price Type (`FREE`/`LOCKED`), Price Amount (BDT).
* **Validation**:
  - Body must not be empty.
  - If Price Type is `LOCKED`, Price Amount must be $\ge 1$ BDT.
* **Processing**:
  - Save the post as a `DRAFT` or `PUBLISHED` content type `POST` under the author's tenant ID.

#### STUDIO-FR-02: Post Edit & Soft Delete
* **Input**: Post ID, Author ID, Title, Body, Price Type, Price Amount.
* **Validation**:
  - Verify that the calling author owns the post.
* **Processing**:
  - Update the post fields.
  - For soft delete, update the `deletedAt` timestamp.

#### STUDIO-FR-03: Book Creation & Statuses
* **Input**: Author ID, Title, Synopsis, Cover Image, default Category Tags.
* **Processing**:
  - Create a new `Book` record under the author's tenant ID. Set status to `DRAFT`.
  - Support the following status transitions:
    - `DRAFT`: Book and all its chapters are hidden from the public directory.
    - `UNFINISHED_PREVIEW` (In-Progress / Early Access): The book is visible in the public catalog. Readers can see and unlock published chapters, write public reviews, and interact with the author while the book is actively being written.
    - `COMPLETED`: The book is marked as complete. All final chapters are published and locked/unlocked as configured.

#### STUDIO-FR-04: Book Update & Delete
* **Input**: Book ID, Author ID, updated fields.
* **Processing**:
  - Update book metadata.
  - On delete, soft-delete the book and recursively soft-delete all chapters inside it.

#### STUDIO-FR-05: Chapter Creation
* **Input**: Book ID, Author ID, Title, Order Index, Body, Price Type, Price Amount.
* **Validation**:
  - Verify book exists and belongs to the author.
* **Processing**:
  - Create a new `Chapter` record linked to the Book.

#### STUDIO-FR-06: Chapter Re-ordering
* **Input**: Book ID, Author ID, Map of Chapter IDs to Order Indices.
* **Processing**:
  - Re-map and save the new chapter sequence indices inside the database transaction.

#### STUDIO-FR-07: Chapter Edit & Soft Delete
* **Input**: Chapter ID, Author ID, updated fields.
* **Processing**:
  - Update the chapter content. On soft delete, flag the chapter as deleted.

#### STUDIO-FR-08: Rich-Media Ingestion (MinIO integration)
* **Input**: Media File (from Editor).
* **Validation**:
  - Max file size 5MB.
* **Processing**:
  - Upload file to MinIO object storage under a tenant-isolated prefix (`minio://books/{tenantId}/uploads/...`).
  - Return the secure presigned storage URL to the client editor.

#### STUDIO-FR-09: Publish Content Event
* **Input**: Content ID (Post or Chapter).
* **Processing**:
  - On publication transition (e.g. `DRAFT` $\rightarrow$ `PUBLISHED`), publish a `ContentPublishedEvent` containing the writing metadata.
  - The `catalog` module listens to this event to index the content in Elasticsearch and make it visible in feeds.

#### STUDIO-FR-10: Reviewer Invitation System
* **Input**: Author ID, Book/Chapter ID, Reviewer Identifier (email or phone).
* **Processing**:
  - Generate a secure, one-time invitation link (`ReviewerInvitation`) containing a token.
  - Upon token redemption by the invitee, register the user as a `Reviewer` for that specific book/chapter.
  - Reviewers are granted read permissions to view `DRAFT` (unpublished) chapters of the invited book.

#### STUDIO-FR-11: Reviewer Private Comments & Suggestions
* **Input**: Reviewer ID, Chapter ID, Selected Text Range (anchored offset coordinates), Comment/Suggestion Text.
* **Validation**:
  - Verify that the calling user is an authorized `Reviewer` or the `Author` of the book.
* **Processing**:
  - Save the highlight coordinates and comment text in the `ReviewerFeedback` table.
  - **Visibility Rule**: These comments, highlights, and suggested changes are strictly **visible only to the author and the specific reviewer** who created them. They must never be exposed to public readers or endpoints.

#### STUDIO-FR-12: Intellectual Property (IP) Dispute & Takedowns
* **Input**: Claimant Author ID, Target Content ID, Copy Violation Evidence (Text comparisons/URLs).
* **Validation**:
  - Verify the target content exists.
* **Processing**:
  - Create an `IpDispute` record.
  - Place the target book/post into `UNDER_REVIEW` state, temporarily hiding it from all public search feeds and catalog indexing.
  - Notify the target author and queue the claim for moderator evaluation.

#### STUDIO-FR-13: B2B Publisher Rights Exchange & Offers
* **Input**: Publisher Rep ID, Target Author ID, Book ID, Print Rights Offer Details (Proposed advances, royalties percentage, legal terms).
* **Validation**:
  - Verify publisher has an active SaaS subscription tier permitting scouting access.
  - Verify target book has a minimum scouting score threshold.
* **Processing**:
  - Register a `RightsOffer` record. Send notification to the target author.
  - If the author accepts, generate a secure contract signing event and record the transaction framework.

---

### C. Reading Module (`reading`)

#### READ-FR-01: Secure Page Server
* **Input**: Reader ID, Content ID, Page Number.
* **Processing**:
  - Verify access rights (see `READ-FR-02`).
  - Retrieve the page text or SVG canvas coordinates for that specific page and return it.

#### READ-FR-02: Access Verification
* **Input**: Reader ID, Content ID (Chapter or Post).
* **Processing**:
  - Check if the content is `FREE`. If yes, grant read access.
  - If `LOCKED`, query the `UnlockedContent` table to check if the reader has purchased this item. If yes, grant access.
  - If no record exists, block request and return `CONTENT_LOCKED` error.

#### READ-FR-03: Dynamic Pixel Watermarking
* **Input**: Reader User Info, Page Data.
* **Processing**:
  - Render the reader's phone number, email, and IP address directly into the canvas drawing paths or SVG overlay before sending page data to the client, ensuring it cannot be hidden via CSS selectors.

#### READ-FR-04: Reader Library Shelf
* **Input**: Reader ID, Book/Post ID, Action (Add/Remove).
* **Processing**:
  - Save or delete the `LibraryEntry` record. List shelf items sorted by `lastReadAt`.

#### READ-FR-05: Reading Progress Save
* **Input**: Reader ID, Content ID, Chapter ID, Page Number.
* **Processing**:
  - Check-in progress ticks and update `LibraryEntry.lastReadPageNum` and `lastReadAt`.

#### READ-FR-06: Reading Progress Resume
* **Input**: Reader ID, Book/Post ID.
* **Processing**:
  - Return the user's last saved reading coordinates (Chapter ID, Page Number) to allow quick resumption.

---

### D. Billing Module (`billing`)

#### BILL-FR-01: Wallet Management
* **Input**: User ID.
* **Processing**:
  - Retrieve and return the user's current wallet ledger balance.

#### BILL-FR-02: SSLCommerz Callback Processing
* **Input**: Transaction ID, SSLCommerz payload (Status, Amount, Validation ID).
* **Validation**:
  - Validate the signature of the SSLCommerz IPN payload.
  - Check if the transaction ID matches a pending wallet deposit request.
* **Processing**:
  - If status is `SUCCESS`, credit the user's wallet with the amount. Update transaction state to `COMPLETED`.
  - Log double-entry ledger line.

#### BILL-FR-03: Idempotent One-Tap Unlock
* **Input**: Reader ID, Content ID, Idempotency-Key.
* **Validation**:
  - Verify Idempotency-Key is not already processed in Redis (24h window).
  - Verify content is locked and not already purchased by the user.
  - Verify reader's wallet balance $\ge$ content price.
* **Processing**:
  - Deduct price from reader's wallet.
  - Insert record into `UnlockedContent` to grant permission.
  - Write transaction to double-entry ledger.

#### BILL-FR-04: Immutable Double-Entry Ledger
* **Input**: Source Wallet ID, Destination Wallet ID, Amount, Type.
* **Processing**:
  - Write credit and debit records to the `billing.ledger` table inside a single database transaction. 
  - Standard Split: 85% to Author's wallet, 15% to Platform commission wallet.

#### BILL-FR-05: Author Earnings Overview
* **Input**: Author ID.
* **Processing**:
  - Query ledger table to return total lifetime earnings, current cash-out balance, and log of previous withdrawals.

#### BILL-FR-06: Author Withdrawal Request
* **Input**: Author ID, Amount BDT, Target MFS Wallet.
* **Validation**:
  - Verify withdrawal amount $\le$ current cash-out balance.
* **Processing**:
  - Deduct amount from author's cash-out balance.
  - Create a pending payout request (`PayoutRequest`) for manual/automated bKash payout processing.

#### BILL-FR-07: Daily Ledger Reconciliation
* **Input**: Scheduler trigger (Daily 2 AM).
* **Processing**:
  - Compare the sum of all transaction logs against the current wallet balances.
  - Flag any anomalies or mismatching balances for administrative audit.

#### BILL-FR-08: Settlement Payout Delay & Disputes
* **Input**: Reader ID, Unlocked Content ID, Dispute Reason.
* **Validation**:
  - Verify the unlock occurred within the last 24 hours.
* **Processing**:
  - When a reader unlocks a chapter, hold the author's share in `PENDING_SETTLEMENT` status for 24 hours.
  - If a reader files a dispute during this window, freeze the settlement.
  - If a moderator approves the dispute (e.g. plagiarized/corrupted content), reverse the transaction, refunding the reader's wallet. If rejected, credit the author's cash-out balance.

#### BILL-FR-09: Growth Hacking Engine (Promo Codes & Referrals)
* **Input**: User ID, Promo Code (or Referral Code).
* **Validation**:
  - Verify code exists, is active, has remaining usage limits, and meets campaign validity rules.
* **Processing**:
  - **Promo Codes**: Grant discounts (e.g., 50% off or free) for locked chapter purchases. Adjust transaction ledgers to log the campaign discount line.
  - **Referrals**: Upon new verified reader sign-up, credit the referrer and referee wallets with campaign reward credits.

#### BILL-FR-10: Reader Affiliate Revenue Split
* **Input**: Buyer ID, Content ID, Affiliate Referrer ID.
* **Validation**:
  - Verify the affiliate connection is active (captured when the referrer shared the quote/link).
* **Processing**:
  - When calculating the payout ledger splits, allocate a marketer's referral share (e.g., 2% of transaction value) to the Referrer's wallet, adjusting the platform split accordingly.

#### BILL-FR-11: Dynamic Commission Matrix
* **Input**: Admin ID, Partner/Tenant ID, Platform Fee Percentage.
* **Validation**:
  - Limit platform fees to a valid range ($5\% \rightarrow 30\%$).
* **Processing**:
  - Allow platform administrators to dynamically adjust split matrices for specific writer tiers or promotional campaigns. The ledger split engine must read from this matrix at the moment of checkout.

#### BILL-FR-12: Escrow Rights Settlement Ledger
* **Input**: B2B Publisher ID, Author ID, Accepted Rights Offer ID, Advance Amount.
* **Processing**:
  - Upon acceptance of print rights, place the publisher's advance BDT in a secure `ESCROW` ledger state.
  - Once digital signatures and MFS confirmations are resolved, transfer BDT to the author's payout ledger and deduct commissions.

#### BILL-FR-13: VAT & Invoice Engine (Bangladesh Tax Compliance)
* **Input**: Wallet Deposit Transaction, Content Purchase Transaction.
* **Processing**:
  - Apply the standard Bangladesh VAT rate (e.g., 5% on SaaS, 15% on transactional sales, or tax-exempt rules for specific ebooks).
  - Generate a secure PDF Invoice containing the transaction hash, tax identification details, and platform splits, emailing it to the user.

#### BILL-FR-14: Instant MFS Timeout/Deposit Dispute Resolution (TxnID Lookup)
* **Input**: Reader ID, bKash/Nagad Transaction ID (`MfsTxnId`), Expected Deposit Amount.
* **Validation**:
  - Check if the Transaction ID is already claimed in the platform database.
* **Processing**:
  - Expose a self-service check `/api/v1/billing/wallet/dispute-resolve` where users can input their MfsTxnId.
  - The system queries the SSLCommerz gateway API (`GET /validator/api/merchantTransIDvalidationAPI.php?val_id=...` or standard status query) to verify the transaction status. If validated as success on the gateway but pending locally, immediately credit the reader's wallet and update transaction state, avoiding support desk delays.

---

### E. Analytics Module (`analytics`)

#### AN-FR-01: Read Engagement Tracking
* **Input**: Reader Session, Chapter/Post ID.
* **Processing**:
  - Register a view event. Filter duplicate views using Redis session hashes (1-hour TTL) to prevent bot-inflated counts.

#### AN-FR-02: Chapter Completion Funnel
* **Input**: Book ID.
* **Processing**:
  - Aggregate unique reader counts for each chapter of the target book.
  - Return a percentage funnel illustrating where readers drop off.

#### AN-FR-03: Author Dashboard Stats
* **Input**: Author ID.
* **Processing**:
  - Return aggregated views, active subscribers, top-performing writings, and average reader session duration.

#### AN-FR-04: Scouting Score Engine
* **Input**: Book/Writing ID.
* **Processing**:
  - Calculate score: $\text{Scouting Score} = (\text{Total Reads} \times 0.3) + (\text{Chapter Completion Rate} \times 0.5) + (\text{Revenue Growth Rate} \times 0.2)$.
  - Highlight top-ranking books on the B2B publisher scouting feed.

#### AN-FR-05: Cohort Retention Analysis
* **Input**: Admin/BA Request, Date Range, Cohort Criteria (Sign-up Month, Payout Range).
* **Processing**:
  - Calculate the weekly and monthly retention metrics (paying users vs. bounce rates) to track customer lifetime value (LTV). Expose data as structured CSV exports.

---

## 3. Non-Functional Requirements (NFR)

* **NFR-01: Scalability & Latency (Boi Mela Readiness)**:
  - Cache read paths (book catalog, public posts, free pages) in Redis. Target latency: < 50ms for cached reads.
  - Use Java 25 Virtual Threads to handle high parallel connection counts during peak transaction periods.
* **NFR-02: Multi-Tenant Data Security**:
  - PostgreSQL Row Level Security (RLS) or tenant schema partitioning must isolate author tables. No tenant should be able to query another tenant's catalog drafts or ledger details.
* **NFR-03: Idempotency**:
  - All payment and wallet transactions must require an `Idempotency-Key` HTTP header. The billing system must discard duplicate requests within a 24-hour window using Redis locks.
