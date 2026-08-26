-- Add cloud sync foundation columns to transactions
-- Preserve existing Merchant/Auth/Device models, only extend transactions

-- Drop FK that ties deviceId to devices.id (cloud sync stores external deviceId string)
ALTER TABLE "transactions" DROP CONSTRAINT IF EXISTS "transactions_deviceId_fkey";

-- Drop legacy unique (deviceId, transactionId) — replaced by transactionUuid unique
DROP INDEX IF EXISTS "transactions_deviceId_transactionId_key";

-- Add new cloud sync columns
ALTER TABLE "transactions" ADD COLUMN IF NOT EXISTS "transactionUuid" TEXT;
ALTER TABLE "transactions" ADD COLUMN IF NOT EXISTS "currency" TEXT NOT NULL DEFAULT 'INR';
ALTER TABLE "transactions" ADD COLUMN IF NOT EXISTS "senderVpa" TEXT;
ALTER TABLE "transactions" ADD COLUMN IF NOT EXISTS "upiReference" TEXT;
ALTER TABLE "transactions" ADD COLUMN IF NOT EXISTS "transactionTime" TIMESTAMP(3);

-- Backfill existing rows: use id as transactionUuid and createdAtDevice/createdAt as transactionTime
UPDATE "transactions" SET "transactionUuid" = "id" WHERE "transactionUuid" IS NULL;
UPDATE "transactions" SET "transactionTime" = COALESCE("createdAtDevice", "createdAt") WHERE "transactionTime" IS NULL;

-- Enforce NOT NULL for new required fields after backfill
ALTER TABLE "transactions" ALTER COLUMN "transactionUuid" SET NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "transactionTime" SET NOT NULL;

-- Make legacy columns nullable for cloud transactions (previously NOT NULL)
ALTER TABLE "transactions" ALTER COLUMN "transactionId" DROP NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "upiApp" DROP NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "createdAtDevice" DROP NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "rawNotification" DROP NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "parserVersion" DROP NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "parseStatus" DROP NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "sourceType" DROP NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "packageName" DROP NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "originalNotificationText" DROP NOT NULL;
ALTER TABLE "transactions" ALTER COLUMN "cleanedNotificationText" DROP NOT NULL;

-- Change status from enum to TEXT to allow String default SUCCESS (keep existing enum values as text)
ALTER TABLE "transactions" ALTER COLUMN "status" TYPE TEXT USING "status"::text;
ALTER TABLE "transactions" ALTER COLUMN "status" SET DEFAULT 'SUCCESS';

-- Allow type to be nullable (legacy)
ALTER TABLE "transactions" ALTER COLUMN "type" DROP NOT NULL;

-- Create unique and indexes for cloud sync
CREATE UNIQUE INDEX IF NOT EXISTS "transactions_transactionUuid_key" ON "transactions"("transactionUuid");
CREATE INDEX IF NOT EXISTS "transactions_transactionUuid_idx" ON "transactions"("transactionUuid");
CREATE INDEX IF NOT EXISTS "transactions_merchantId_transactionTime_idx" ON "transactions"("merchantId", "transactionTime");
CREATE INDEX IF NOT EXISTS "transactions_upiReference_idx" ON "transactions"("upiReference");
