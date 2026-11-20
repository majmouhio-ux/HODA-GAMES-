const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildProfilePayload(uid) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    callsign: "صقر الصحراء",
    rankTitle: "جندي أول",
    xp: 0,
    totalKills: 0,
    headshots: 0,
    matchesPlayed: 0,
    wins: 0,
    bestScore: 0,
    equippedWeapon: "M4A1",
    equippedTactical: "AIRSTRIKE",
    equippedAttachment: "HOLO_OPTIC",
    preferredMap: "DESERT_STORM",
    createdAt: now,
    updatedAt: now,
  };
}

function buildBattleRecordPayload(uid) {
  return {
    userId: uid,
    mapId: "DESERT_STORM",
    modeId: "BATTLE_ROYALE",
    weaponId: "M4A1",
    kills: 14,
    headshots: 5,
    damageDealt: 2450,
    survivalSeconds: 312,
    score: 4850,
    outcome: "VICTORY",
    weathersEncountered: ["CLEAR_DAY", "SANDSTORM", "HEAVY_RAIN"],
    createdAt: new Date(Date.now() - 1000),
  };
}

test("Unauthenticated user cannot read or write profiles or match records", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(buildProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("matches").get()
  );
});

test("Authenticated owner can create and partially update SoldierProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(buildProfilePayload(ALICE_UID)));

  // Action 1: Loadout update
  await assertSucceeds(
    profileRef.update({
      equippedWeapon: "AWM",
      equippedAttachment: "SUPPRESSOR",
      preferredMap: "URBAN_SIEGE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Action 2: Post-match career stats update
  await assertSucceeds(
    profileRef.update({
      rankTitle: "عريف تكتيكي",
      xp: 1200,
      totalKills: 14,
      headshots: 5,
      matchesPlayed: 1,
      wins: 1,
      bestScore: 4850,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject invalid weapon enum
  await assertFails(
    profileRef.update({
      equippedWeapon: "LASER_CANNON",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject future timestamp
  await assertFails(
    profileRef.update({
      callsign: "Phantom",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Cross-user isolation prevents Bob from reading or writing Alice's profile or matches", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set(buildProfilePayload(ALICE_UID));
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("matches")
      .doc("match_1")
      .set(buildBattleRecordPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      callsign: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("matches").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("matches").doc("match_2").set(buildBattleRecordPayload(BOB_UID))
  );
});

test("Authenticated owner can create and list immutable BattleRecords", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const matchesCol = aliceDb.collection("users").doc(ALICE_UID).collection("matches");

  // Empty list query succeeds without undefined existing() error
  await assertSucceeds(matchesCol.orderBy("createdAt", "desc").limit(20).get());

  // Valid create succeeds
  await assertSucceeds(matchesCol.doc("match_1").set(buildBattleRecordPayload(ALICE_UID)));

  // Extra @DocumentId 'id' field rejected
  await assertFails(
    matchesCol.doc("match_2").set({
      id: "match_2",
      ...buildBattleRecordPayload(ALICE_UID),
    })
  );

  // Update is forbidden (immutable combat history)
  await assertFails(
    matchesCol.doc("match_1").update({
      kills: 99,
    })
  );
});
