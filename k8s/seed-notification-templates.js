
// MongoDB seed script — run once against the notifications DB.
// The connection (and therefore the target DB) comes from the mongosh connection
// string, NOT hardcoded — so this works against MongoDB Atlas (prod) exactly as
// the service does. Do NOT re-add a connect() here; prod uses Atlas, not the old
// in-cluster mongodb-svc.
//
// Usage — run against whatever MONGODB_URI the service uses (Atlas in prod). The
// URI already contains the DB name (prod: /notifications_db), so pass it straight:
//
//   # From a throwaway pod (default ns — avoids the banking-ns istio injector):
//   URI=$(kubectl get secret mongodb-atlas-secret -n banking -o jsonpath='{.data.notifications-uri}' | base64 -d)
//   kubectl run mongo-seed --rm -i --restart=Never -n default --image=mongo:7 \
//     --command -- sh -c "mongosh '$URI'" < k8s/seed-notification-templates.js
//
//   # Or locally (requires mongosh + your IP in the Atlas access list):
//   mongosh "$MONGODB_URI" seed-notification-templates.js
//
// Re-running is safe: upsert on eventType (unique index).

// Use the DB from the connection string (e.g. Atlas .../notifications_db).
const now = new Date();

const templates = [
  {
    eventType:     "ACCOUNT_CREATED",
    smsTemplate:   "Dear {fullName}, your RewaBank {accountType} account ({accountNumber}) has been opened and is pending KYC verification. Complete KYC to activate it.",
    emailSubject:  "Your RewaBank account has been opened",
    emailTemplate: "Dear {fullName},\n\nYour {accountType} account has been successfully opened with account number {accountNumber}.\n\nYour account is currently PENDING activation. To start using it, please complete your KYC verification at your earliest convenience.\n\nIf you have any questions, contact RewaBank support.\n\nRegards,\nRewaBank Team",
    pushTitle:     "Account Opened",
    pushTemplate:  "Your {accountType} account {accountNumber} is open and awaiting KYC verification.",
    smsEnabled:    true,
    emailEnabled:  true,
    pushEnabled:   false,
    version:       1,
    createdAt:     now,
    updatedAt:     now
  },
  {
    eventType:     "ACCOUNT_ACTIVATED",
    smsTemplate:   "Dear {fullName}, your RewaBank {accountType} account ({accountNumber}) is now ACTIVE. You can start transacting immediately.",
    emailSubject:  "Your RewaBank account is now active",
    emailTemplate: "Dear {fullName},\n\nGreat news! Your {accountType} account ({accountNumber}) has been verified and is now ACTIVE.\n\nYou can now make deposits, withdrawals, and transfers.\n\nRegards,\nRewaBank Team",
    pushTitle:     "Account Activated",
    pushTemplate:  "Your {accountType} account {accountNumber} is now active!",
    smsEnabled:    true,
    emailEnabled:  true,
    pushEnabled:   false,
    version:       1,
    createdAt:     now,
    updatedAt:     now
  },
  {
    eventType:     "ACCOUNT_FROZEN",
    smsTemplate:   "Alert: Your RewaBank account {accountNumber} has been frozen. Contact support immediately if this was unexpected.",
    emailSubject:  "Your RewaBank account has been frozen",
    emailTemplate: "Dear Customer,\n\nYour account {accountNumber} has been frozen. Please contact RewaBank support for assistance.\n\nRegards,\nRewaBank Team",
    pushTitle:     "Account Frozen",
    pushTemplate:  "Your account {accountNumber} has been frozen. Contact support.",
    smsEnabled:    true,
    emailEnabled:  true,
    pushEnabled:   false,
    version:       1,
    createdAt:     now,
    updatedAt:     now
  },
  {
    eventType:     "ACCOUNT_CLOSED",
    smsTemplate:   "Your RewaBank account {accountNumber} has been closed as requested. Thank you for banking with us.",
    emailSubject:  "Your RewaBank account has been closed",
    emailTemplate: "Dear Customer,\n\nYour account {accountNumber} has been successfully closed.\n\nThank you for banking with RewaBank.\n\nRegards,\nRewaBank Team",
    pushTitle:     "Account Closed",
    pushTemplate:  "Your account {accountNumber} has been closed.",
    smsEnabled:    true,
    emailEnabled:  true,
    pushEnabled:   false,
    version:       1,
    createdAt:     now,
    updatedAt:     now
  }
];

let inserted = 0, updated = 0;

templates.forEach(t => {
  const result = db.notification_templates.updateOne(
    { eventType: t.eventType },
    { $setOnInsert: t },
    { upsert: true }
  );
  if (result.upsertedCount > 0) {
    inserted++;
    print("Inserted template: " + t.eventType);
  } else {
    updated++;
    print("Already exists (skipped): " + t.eventType);
  }
});

print("\nDone. Inserted: " + inserted + " | Already existed: " + updated);
