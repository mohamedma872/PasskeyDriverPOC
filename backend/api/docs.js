/**
 * GET /api/docs
 * Returns the OpenAPI 3.0 JSON spec for this backend.
 */
export default function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  if (req.method !== "GET") return res.status(405).json({ error: "GET only" });

  const spec = {
    openapi: "3.0.3",
    info: {
      title: "WasteHero Fleet API",
      version: "2.0.0",
      description:
        "Driver authentication and fleet management API.\n\n" +
        "### Auth flow (Option B — driver-picker first)\n" +
        "1. `GET /api/fleet/drivers` — app loads the driver list\n" +
        "2. Driver selects their name on the picker screen\n" +
        "3. `POST /api/fleet/verify` `{ driverId, pin }` — PIN confirmed server-side with Argon2id\n" +
        "4. `POST /api/fleet/verify` `{ driverId, embedding }` — face verified with cosine similarity\n\n" +
        "### PIN security\n" +
        "Raw PIN is sent over HTTPS. Server hashes it with **Argon2id** (64 MB memory, 3 iterations). " +
        "The hash is stored in the `pin_argon2` column. No PIN-derived value is stored on the device.\n\n" +
        "All fleet endpoints require `Authorization: Bearer <SYNC_SECRET>`.",
    },
    servers: [{ url: "https://passkey-backend-tau.vercel.app" }],
    security: [],
    components: {
      securitySchemes: {
        BearerAuth: {
          type: "http",
          scheme: "bearer",
          description: "SYNC_SECRET environment variable value",
        },
      },
      schemas: {
        Error: {
          type: "object",
          properties: { error: { type: "string", example: "Driver not found" } },
        },
        DriverInfo: {
          type: "object",
          properties: {
            id:         { type: "string",  example: "uuid-1234" },
            name:       { type: "string",  example: "Ahmed Khalil" },
            isEnrolled: { type: "boolean", example: true, description: "Has face embeddings stored" },
          },
        },
        DriversListResponse: {
          type: "object",
          properties: {
            drivers: {
              type: "array",
              items: { $ref: "#/components/schemas/DriverInfo" },
            },
          },
        },
        PinCheckResponse: {
          type: "object",
          properties: {
            exists:   { type: "boolean", example: true },
            driverId: { type: "string",  example: "uuid-1234" },
            name:     { type: "string",  example: "Ahmed Khalil" },
          },
        },
        FaceVerifyResponse: {
          type: "object",
          properties: {
            matched:  { type: "boolean", example: true },
            score:    { type: "number",  format: "float", example: 0.87, description: "Cosine similarity 0–1" },
            driverId: { type: "string",  example: "uuid-1234" },
            name:     { type: "string",  example: "Ahmed Khalil" },
          },
        },
        DuplicateCheckResponse: {
          type: "object",
          properties: {
            isDuplicate: { type: "boolean", example: true },
            name: {
              type: "string",
              example: "Ahmed Khalil",
              description: "Name of the matching driver. Present only when isDuplicate is true.",
            },
            score: {
              type: "number",
              format: "float",
              example: 0.91,
              description: "Highest cosine similarity score found. Present only when isDuplicate is true.",
            },
          },
        },
        RegisterRequest: {
          type: "object",
          required: ["id", "name", "pin", "embeddings"],
          properties: {
            id:   { type: "string", example: "uuid-1234", description: "UUID generated on device" },
            name: { type: "string", example: "Ahmed Khalil" },
            pin:  {
              type: "string",
              example: "483920",
              description: "Raw 6-digit PIN — server hashes with Argon2id (memoryCost=65536, timeCost=3)",
            },
            embeddings: {
              type: "array",
              items: { type: "array", items: { type: "number", format: "float" } },
              description: "5 × 512-float FaceNet embeddings captured during enrollment",
            },
          },
        },
      },
    },
    paths: {
      "/api/fleet/drivers": {
        get: {
          summary: "List all registered drivers",
          description:
            "Returns every driver row ordered by name. Used by the Android driver-picker screen. " +
            "Drivers with `isEnrolled: false` are shown dimmed and cannot log in until they complete face enrollment.",
          tags: ["Fleet Auth"],
          security: [{ BearerAuth: [] }],
          responses: {
            200: {
              description: "List of drivers",
              content: {
                "application/json": {
                  schema: { $ref: "#/components/schemas/DriversListResponse" },
                  example: {
                    drivers: [
                      { id: "uuid-1234", name: "Ahmed Khalil",    isEnrolled: true  },
                      { id: "uuid-5678", name: "Mohamed Elsdody", isEnrolled: false },
                    ],
                  },
                },
              },
            },
            401: { description: "Unauthorized", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
            500: { description: "Server error", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
          },
        },
      },
      "/api/fleet/verify": {
        post: {
          summary: "Step 1: PIN check — Step 2: Face verify",
          description:
            "Called **twice** during login:\n\n" +
            "**Step 1 — PIN check** `{ driverId, pin }`:\n" +
            "Driver is already identified from the picker screen. Server looks up the driver by `driverId`, " +
            "runs `argon2.verify(storedHash, pin)` (Argon2id, 64 MB memory cost, ~300 ms). " +
            "Returns `{ exists, driverId, name }`. Android navigates to the face camera screen.\n\n" +
            "**Step 2 — Face verify** `{ driverId, embedding }`:\n" +
            "FaceNet runs **on-device** (Android TFLite) and produces a 512-float L2-normalised embedding — " +
            "this cannot be generated manually from Swagger. The sample below uses 512 zeros as a placeholder; " +
            "to get a real result you must call this from the app after the liveness challenge passes. " +
            "Server decrypts stored embeddings (AES-256-GCM) and computes cosine similarity against all 5 stored captures. " +
            "Returns `{ matched, score, driverId, name }`. Threshold configurable via `FACE_THRESHOLD` env var (default 0.75).",
          tags: ["Fleet Auth"],
          security: [{ BearerAuth: [] }],
          requestBody: {
            required: true,
            content: {
              "application/json": {
                schema: {
                  type: "object",
                  properties: {
                    driverId: {
                      type: "string",
                      description: "Driver UUID — required for both steps",
                      example: "uuid-1234",
                    },
                    pin: {
                      type: "string",
                      description: "Raw 6-digit PIN — include for Step 1, omit for Step 2",
                      example: "483920",
                    },
                    embedding: {
                      type: "array",
                      items: { type: "number", format: "float" },
                      description: "512-float FaceNet embedding — include for Step 2, omit for Step 1",
                    },
                  },
                },
                examples: {
                  pinCheck: {
                    summary: "Step 1 — PIN check",
                    value: { driverId: "uuid-1234", pin: "483920" },
                  },
                  faceVerify: {
                    summary: "Step 2 — Face verify (use app for real embedding)",
                    value: { driverId: "uuid-1234", embedding: Array(512).fill(0) },
                  },
                },
              },
            },
          },
          responses: {
            200: {
              description: "PIN confirmed (Step 1) or face match result (Step 2)",
              content: {
                "application/json": {
                  schema: {
                    oneOf: [
                      { $ref: "#/components/schemas/PinCheckResponse" },
                      { $ref: "#/components/schemas/FaceVerifyResponse" },
                    ],
                  },
                },
              },
            },
            400: { description: "Driver has no PIN set / not enrolled / bad request body", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
            401: { description: "Unauthorized (wrong Bearer token) or incorrect PIN", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
            404: { description: "Driver not found", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
            500: { description: "Server error", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
          },
        },
      },
      "/api/fleet/check-duplicate": {
        post: {
          summary: "Check if a face is already registered",
          description:
            "Called during self-registration after the first face capture. " +
            "Computes cosine similarity between the given embedding and every enrolled driver's stored embeddings. " +
            "If any match exceeds the threshold (`FACE_THRESHOLD` env var, default 0.75), returns `isDuplicate: true` " +
            "with the matching driver's name so the app can block the duplicate registration. " +
            "Network errors are treated as non-duplicate (fail-open) on the Android side to avoid blocking legitimate new drivers.",
          tags: ["Fleet Auth"],
          security: [{ BearerAuth: [] }],
          requestBody: {
            required: true,
            content: {
              "application/json": {
                schema: {
                  type: "object",
                  required: ["embedding"],
                  properties: {
                    embedding: {
                      type: "array",
                      items: { type: "number", format: "float" },
                      description: "512-float FaceNet embedding from the first enrollment capture",
                    },
                  },
                },
                example: { embedding: Array(512).fill(0) },
              },
            },
          },
          responses: {
            200: {
              description: "Duplicate check result",
              content: {
                "application/json": {
                  schema: { $ref: "#/components/schemas/DuplicateCheckResponse" },
                  examples: {
                    notDuplicate: { summary: "New face", value: { isDuplicate: false } },
                    duplicate: { summary: "Already registered", value: { isDuplicate: true, name: "Ahmed Khalil", score: 0.91 } },
                  },
                },
              },
            },
            400: { description: "embedding missing or not a float[512]", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
            401: { description: "Unauthorized", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
            500: { description: "Server error", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
          },
        },
      },
      "/api/fleet/register": {
        post: {
          summary: "Register / enrol a new driver",
          description:
            "Hashes the raw PIN with **Argon2id** (memoryCost=65536 KB, timeCost=3) server-side, " +
            "encrypts face embeddings with AES-256-GCM, and upserts the driver record into Neon PostgreSQL. " +
            "PIN uniqueness is **not** enforced — each driver's PIN is an independent credential " +
            "verified against their own stored hash.",
          tags: ["Fleet Auth"],
          security: [{ BearerAuth: [] }],
          requestBody: {
            required: true,
            content: {
              "application/json": {
                schema: { $ref: "#/components/schemas/RegisterRequest" },
                example: {
                  id:   "uuid-1234",
                  name: "Ahmed Khalil",
                  pin:  "483920",
                  embeddings: Array(5).fill(Array(512).fill(0)),
                },
              },
            },
          },
          responses: {
            200: {
              description: "Driver registered successfully",
              content: { "application/json": { schema: { type: "object", properties: { ok: { type: "boolean", example: true } } } } },
            },
            400: { description: "Missing required fields (id, name, pin)", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
            401: { description: "Unauthorized", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
            500: { description: "Server error during registration", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
          },
        },
      },
      "/api/setup": {
        post: {
          summary: "Initialize database schema (one-time / idempotent)",
          description:
            "Creates `fleet_drivers` table if it does not exist and runs any pending column migrations. " +
            "Safe to call multiple times. Protected by `?secret=SETUP_SECRET` query param.",
          tags: ["Admin"],
          security: [],
          parameters: [
            { in: "query", name: "secret", required: true, schema: { type: "string" }, description: "SETUP_SECRET env var value" },
          ],
          responses: {
            200: { description: "Schema initialized", content: { "application/json": { schema: { type: "object", properties: { ok: { type: "boolean" }, message: { type: "string" } } } } } },
            401: { description: "Wrong secret", content: { "application/json": { schema: { $ref: "#/components/schemas/Error" } } } },
          },
        },
      },
    },
  };

  res.setHeader("Content-Type", "application/json");
  return res.json(spec);
}
