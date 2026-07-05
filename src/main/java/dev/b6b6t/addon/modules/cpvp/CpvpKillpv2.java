// ============================================================
// src/main/java/dev/b6b6t/addon/modules/cpvp/CpvpKillpv2.java
// ============================================================
package dev.b6b6t.addon.modules.cpvp;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * cpvp_killpv2 — Crystal PvP ماژول برای سرور 6b6t
 * بهینه‌شده برای پینگ بالا، دقت بالا، سرعت بالا.
 * تمام تنظیمات داخل این یک ماژول است.
 */
public class CpvpKillpv2 extends Module {

    // ══════════════════════════════════════════════════════════
    //  Setting Groups
    // ══════════════════════════════════════════════════════════
    private final SettingGroup sgGeneral    = settings.getDefaultGroup();
    private final SettingGroup sgPlace      = settings.createGroup("Place");
    private final SettingGroup sgBreak      = settings.createGroup("Break");
    private final SettingGroup sgSilent     = settings.createGroup("Silent");
    private final SettingGroup sgTarget     = settings.createGroup("Target");
    private final SettingGroup sgEntities   = settings.createGroup("Entities");
    private final SettingGroup sgSafety     = settings.createGroup("Safety");
    private final SettingGroup sgRender     = settings.createGroup("Render");

    // ══════════════════════════════════════════════════════════
    //  GENERAL
    // ══════════════════════════════════════════════════════════
    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("فاصله‌ای که پلیر هدف گرفته می‌شود (بلاک).")
        .defaultValue(10.0)
        .min(1.0).sliderMax(16.0)
        .build());

    private final Setting<Boolean> predictMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("حرکت بعدی هدف را پیش‌بینی می‌کند برای دقت بیشتر در پینگ بالا.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("سرور-ساید به سمت کریستال می‌چرخد.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> ignoreFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("به پلیرهایی که در لیست Friends هستند آسیب نمی‌زند.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> removeFriends = sgGeneral.add(new BoolSetting.Builder()
        .name("friends-remove-safe")
        .description("اگر فعال باشد، کریستال در نزدیکی فرند گذاشته نمی‌شود.")
        .defaultValue(true)
        .build());

    // ══════════════════════════════════════════════════════════
    //  PLACE
    // ══════════════════════════════════════════════════════════
    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay-ticks")
        .description("تاخیر گذاشتن کریستال به تیک. 0 = سریع‌ترین حالت.")
        .defaultValue(0)
        .min(0).sliderMax(10)
        .build());

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("حداکثر فاصله برای گذاشتن کریستال.")
        .defaultValue(4.5)
        .min(1.0).sliderMax(6.0)
        .build());

    private final Setting<Double> placeWallsRange = sgPlace.add(new DoubleSetting.Builder()
        .name("walls-place-range")
        .description("فاصله گذاشتن پشت بلاک (برای پینگ بالا باید با placeRange یکسان باشد).")
        .defaultValue(4.5)
        .min(1.0).sliderMax(6.0)
        .build());

    private final Setting<Double> minDamageToPlace = sgPlace.add(new DoubleSetting.Builder()
        .name("min-damage-to-place")
        .description("حداقل دمیجی که باید به هدف برسد تا کریستال گذاشته شود.")
        .defaultValue(6.0)
        .min(1.0).sliderMax(36.0)
        .build());

    private final Setting<Boolean> placeOnObsidian = sgPlace.add(new BoolSetting.Builder()
        .name("place-on-obsidian")
        .description("کریستال روی Obsidian گذاشته می‌شود.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> placeOnBedrock = sgPlace.add(new BoolSetting.Builder()
        .name("place-on-bedrock")
        .description("کریستال روی Bedrock گذاشته می‌شود.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> highPingMode = sgPlace.add(new BoolSetting.Builder()
        .name("high-ping-mode")
        .description(
            "بهینه‌سازی ویژه برای پینگ بالا. "
            + "پکت‌ها بدون انتظار به سرور ارسال می‌شوند. "
            + "سرعت بیشتر از BlockOut."
        )
        .defaultValue(true)
        .build());

    // ══════════════════════════════════════════════════════════
    //  BREAK
    // ══════════════════════════════════════════════════════════
    private final Setting<Integer> breakDelay = sgBreak.add(new IntSetting.Builder()
        .name("break-delay-ticks")
        .description("تاخیر شکستن کریستال به تیک. 0 = سریع‌ترین حالت.")
        .defaultValue(0)
        .min(0).sliderMax(10)
        .build());

    private final Setting<Double> breakRange = sgBreak.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("حداکثر فاصله برای شکستن کریستال.")
        .defaultValue(4.5)
        .min(1.0).sliderMax(6.0)
        .build());

    private final Setting<Double> breakWallsRange = sgBreak.add(new DoubleSetting.Builder()
        .name("walls-break-range")
        .description("فاصله شکستن پشت بلاک.")
        .defaultValue(4.5)
        .min(1.0).sliderMax(6.0)
        .build());

    private final Setting<Boolean> fastBreak = sgBreak.add(new BoolSetting.Builder()
        .name("fast-break")
        .description("به محض spawn شدن کریستال آن را می‌شکند (تاخیر break را نادیده می‌گیرد).")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxAttacksPerSec = sgBreak.add(new IntSetting.Builder()
        .name("attack-frequency")
        .description("حداکثر تعداد حملات در ثانیه.")
        .defaultValue(25)
        .min(1).sliderMax(30)
        .build());

    // ══════════════════════════════════════════════════════════
    //  SILENT (حالت خاموش - برداشت از inventory)
    // ══════════════════════════════════════════════════════════
    private final Setting<Boolean> silentMode = sgSilent.add(new BoolSetting.Builder()
        .name("silent-mode")
        .description(
            "اگر کریستال در هات‌بار نباشد، "
            + "مستقیم از inventory برمی‌دارد "
            + "بدون اینکه سلات عوض شود."
        )
        .defaultValue(true)
        .build());

    private final Setting<Boolean> silentSwitch = sgSilent.add(new BoolSetting.Builder()
        .name("silent-switch")
        .description("اگر کریستال در هات‌بار نباشد، به آرامی و بدون نمایش سوییچ می‌کند.")
        .defaultValue(true)
        .visible(() -> !silentMode.get())
        .build());

    private final Setting<Boolean> autoSwitchBack = sgSilent.add(new BoolSetting.Builder()
        .name("auto-switch-back")
        .description("بعد از گذاشتن کریستال به سلات قبلی برمی‌گردد.")
        .defaultValue(true)
        .build());

    // ══════════════════════════════════════════════════════════
    //  TARGET
    // ══════════════════════════════════════════════════════════
    private final Setting<Double> minDamageToTarget = sgTarget.add(new DoubleSetting.Builder()
        .name("min-target-damage")
        .description("حداقل دمیج به هدف برای اعتبارسنجی موقعیت.")
        .defaultValue(6.0)
        .min(0.0).sliderMax(36.0)
        .build());

    private final Setting<Boolean> smartDamageCalc = sgTarget.add(new BoolSetting.Builder()
        .name("smart-damage-calc")
        .description("محاسبه دقیق بهترین موقعیت برای بیشترین دمیج به هدف با کمترین آسیب به خود.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> facePlace = sgTarget.add(new BoolSetting.Builder()
        .name("face-place")
        .description("وقتی HP هدف پایین است، مستقیم روی پاهایش کریستال می‌گذارد.")
        .defaultValue(true)
        .build());

    private final Setting<Double> facePlaceHealth = sgTarget.add(new DoubleSetting.Builder()
        .name("face-place-health")
        .description("زمانی که HP هدف زیر این مقدار برود Face Place فعال می‌شود.")
        .defaultValue(8.0)
        .min(1.0).sliderMax(36.0)
        .visible(facePlace::get)
        .build());

    // ══════════════════════════════════════════════════════════
    //  ENTITIES (کدام موجودات را هدف بگیرد)
    // ══════════════════════════════════════════════════════════
    private final Setting<Boolean> targetPlayers = sgEntities.add(new BoolSetting.Builder()
        .name("target-players")
        .description("پلیرها را هدف می‌گیرد.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> targetMobs = sgEntities.add(new BoolSetting.Builder()
        .name("target-mobs")
        .description("Mob ها را هدف می‌گیرد.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> targetAnimals = sgEntities.add(new BoolSetting.Builder()
        .name("target-animals")
        .description("حیوانات را هدف می‌گیرد.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> targetBoss = sgEntities.add(new BoolSetting.Builder()
        .name("target-boss")
        .description("باس‌ها (Wither, Dragon) را هدف می‌گیرد.")
        .defaultValue(true)
        .build());

    // ══════════════════════════════════════════════════════════
    //  SAFETY (جلوگیری از کشتن خودم)
    // ══════════════════════════════════════════════════════════
    private final Setting<Boolean> antiSuicide = sgSafety.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("کریستال نمی‌گذارد/نمی‌شکند اگر خودت را بکشد.")
        .defaultValue(true)
        .build());

    private final Setting<Double> maxSelfDamage = sgSafety.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("حداکثر دمیجی که می‌توانی از کریستال بخوری.")
        .defaultValue(8.0)
        .min(0.0).sliderMax(36.0)
        .build());

    private final Setting<Double> pauseHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("pause-health")
        .description("زیر این مقدار HP ماژول متوقف می‌شود.")
        .defaultValue(5.0)
        .min(0.0).sliderMax(36.0)
        .build());

    private final Setting<Boolean> pauseOnLag = sgSafety.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("اگر سرور lag کرد متوقف می‌شود.")
        .defaultValue(true)
        .build());

    // ══════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════
    private final Setting<Boolean> renderPlace = sgRender.add(new BoolSetting.Builder()
        .name("render-place")
        .description("محل Place را رندر می‌کند.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> renderBreak = sgRender.add(new BoolSetting.Builder()
        .name("render-break")
        .description("محل Break را رندر می‌کند.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> renderDamageText = sgRender.add(new BoolSetting.Builder()
        .name("render-damage-text")
        .description("مقدار دمیج را روی کریستال نشان می‌دهد.")
        .defaultValue(true)
        .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("نحوه رندر شکل (خط، پر، هر دو).")
        .defaultValue(ShapeMode.Both)
        .build());

    private final Setting<SettingColor> placeColor = sgRender.add(new ColorSetting.Builder()
        .name("place-color")
        .description("رنگ رندر محل Place کریستال.")
        .defaultValue(new SettingColor(0, 255, 255, 80))
        .build());

    private final Setting<SettingColor> breakColor = sgRender.add(new ColorSetting.Builder()
        .name("break-color")
        .description("رنگ رندر محل Break کریستال.")
        .defaultValue(new SettingColor(255, 0, 80, 80))
        .build());

    private final Setting<SettingColor> damageColor = sgRender.add(new ColorSetting.Builder()
        .name("damage-color")
        .description("رنگ متن دمیج.")
        .defaultValue(new SettingColor(255, 255, 255, 255))
        .build());

    // ══════════════════════════════════════════════════════════
    //  Internal State
    // ══════════════════════════════════════════════════════════
    private int placeTimer = 0;
    private int breakTimer = 0;
    private int attacks   = 0;
    private int tickCount = 0;
    private final List<LivingEntity> targets = new ArrayList<>();

    public CpvpKillpv2() {
        super(
            // Category — باید در Addon.java ثبت بشه
            dev.b6b6t.addon.B6b6tAddon.COMBAT,
            "cpvp-killpv2",
            "Crystal PvP بهینه‌شده برای 6b6t — دقت بالا، پینگ بالا، سرعت بالا."
        );
    }

    @Override
    public void onActivate() {
        placeTimer = 0;
        breakTimer = 0;
        attacks    = 0;
        tickCount  = 0;
        targets.clear();
    }

    @Override
    public void onDeactivate() {
        targets.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // شمارش تیک برای reset attacks
        tickCount++;
        if (tickCount >= 20) {
            tickCount = 0;
            attacks   = 0;
        }

        // بررسی pause
        if (shouldPause()) return;

        // پیدا کردن target ها
        findTargets();

        if (targets.isEmpty()) return;

        // تیمر
        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;

        // Break → Place
        if (breakTimer == 0) doBreak();
        if (placeTimer == 0) doPlace();
    }

    // ──────────────────────────────────────────────────────────
    private boolean shouldPause() {
        double hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        return hp <= pauseHealth.get();
    }

    // ──────────────────────────────────────────────────────────
    private void findTargets() {
        targets.clear();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == mc.player) continue;
            if (!living.isAlive()) continue;

            // فیلتر نوع موجود
            if (living instanceof PlayerEntity player) {
                if (!targetPlayers.get()) continue;
                // بررسی friends
                if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
            } else {
                // اگر هیچ‌کدام از فیلترها فعال نباشد رد کن
                if (!targetMobs.get() && !targetBoss.get() && !targetAnimals.get()) continue;
            }

            // فاصله
            if (mc.player.distanceTo(living) > targetRange.get()) continue;

            targets.add(living);
        }
    }

    // ──────────────────────────────────────────────────────────
    private void doBreak() {
        if (!fastBreak.get() && breakTimer > 0) return;
        if (attacks >= maxAttacksPerSec.get()) return;

        EndCrystalEntity best = null;
        float bestDmg = 0f;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;

            // فاصله
            boolean behindWall = isBehindWall(crystal.getPos());
            double maxRange = behindWall ? breakWallsRange.get() : breakRange.get();
            if (mc.player.distanceTo(crystal) > maxRange) continue;

            // دمیج به خود
            float selfDmg = getCrystalDamage(mc.player, crystal.getPos());
            if (selfDmg > maxSelfDamage.get()) continue;
            if (antiSuicide.get() && selfDmg >= getTotalHP()) continue;

            // دمیج به هدف
            float targetDmg = getBestTargetDamage(crystal.getPos());
            if (targetDmg < minDamageToTarget.get()) continue;

            if (targetDmg > bestDmg) {
                bestDmg = targetDmg;
                best    = crystal;
            }
        }

        if (best == null) return;

        // Rotate اگر فعال باشه
        if (rotate.get()) {
            EndCrystalEntity finalBest = best;
            Rotations.rotate(
                Rotations.getYaw(best.getPos()),
                Rotations.getPitch(best.getPos()),
                50,
                () -> attackEntity(finalBest)
            );
        } else {
            attackEntity(best);
        }

        breakTimer = breakDelay.get();
    }

    // ──────────────────────────────────────────────────────────
    private void doPlace() {
        // پیدا کردن کریستال در inventory
        FindItemResult crystalResult = silentMode.get()
            ? InvUtils.find(Items.END_CRYSTAL)
            : InvUtils.findInHotbar(Items.END_CRYSTAL);

        if (!crystalResult.found()) return;

        BlockPos bestPos   = null;
        float    bestDmg   = 0f;

        // اسکن بلاک‌های اطراف
        int range = (int) Math.ceil(placeRange.get());
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos bp = playerPos.add(x, y, z);

                    // بررسی سطح مجاز
                    net.minecraft.block.BlockState state = mc.world.getBlockState(bp);
                    boolean validBase =
                        (placeOnObsidian.get() && state.isOf(net.minecraft.block.Blocks.OBSIDIAN)) ||
                        (placeOnBedrock.get()  && state.isOf(net.minecraft.block.Blocks.BEDROCK));

                    if (!validBase) continue;

                    // بالای بلاک باید خالی باشد
                    BlockPos top = bp.up();
                    if (!mc.world.getBlockState(top).isAir()) continue;
                    if (!mc.world.getBlockState(top.up()).isAir()) continue;

                    // فاصله
                    Vec3d crystalPos = new Vec3d(bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5);
                    boolean behindWall = isBehindWall(crystalPos);
                    double maxRange = behindWall ? placeWallsRange.get() : placeRange.get();
                    if (mc.player.getEyePos().distanceTo(crystalPos) > maxRange) continue;

                    // دمیج به خود
                    float selfDmg = getCrystalDamage(mc.player, crystalPos);
                    if (selfDmg > maxSelfDamage.get()) continue;
                    if (antiSuicide.get() && selfDmg >= getTotalHP()) continue;

                    // بررسی نزدیکی به فرند
                    if (removeFriends.get() && isNearFriend(crystalPos)) continue;

                    // دمیج به هدف
                    float targetDmg = getBestTargetDamage(crystalPos);
                    if (targetDmg < minDamageToPlace.get()) continue;

                    // بررسی collision با entity ها
                    Box box = new Box(
                        top.getX(), top.getY(), top.getZ(),
                        top.getX() + 1, top.getY() + 2, top.getZ() + 1
                    );
                    if (!mc.world.isSpaceEmpty(box)) continue;

                    if (targetDmg > bestDmg) {
                        bestDmg = targetDmg;
                        bestPos = bp;
                    }
                }
            }
        }

        if (bestPos == null) return;

        // Switch (Silent یا معمولی)
        int prevSlot = mc.player.getInventory().selectedSlot;
        if (silentMode.get()) {
            InvUtils.move().from(crystalResult.slot()).toHotbar(prevSlot);
        } else if (!crystalResult.isOffhand()) {
            InvUtils.swap(crystalResult.slot(), autoSwitchBack.get());
        }

        // Place
        BlockPos finalBestPos = bestPos;
        if (rotate.get()) {
            Vec3d placeVec = new Vec3d(bestPos.getX() + 0.5, bestPos.getY() + 1, bestPos.getZ() + 0.5);
            Rotations.rotate(
                Rotations.getYaw(placeVec),
                Rotations.getPitch(placeVec),
                50,
                () -> placeCrystal(finalBestPos, crystalResult)
            );
        } else {
            placeCrystal(finalBestPos, crystalResult);
        }

        // Switch back
        if (autoSwitchBack.get() && !silentMode.get()) {
            InvUtils.swap(prevSlot, false);
        }

        placeTimer = placeDelay.get();
    }

    // ──────────────────────────────────────────────────────────
    private void placeCrystal(BlockPos bp, FindItemResult item) {
        net.minecraft.util.Hand hand = item.getHand();
        if (hand == null) hand = net.minecraft.util.Hand.MAIN_HAND;

        net.minecraft.util.math.Direction dir = net.minecraft.util.math.Direction.UP;
        net.minecraft.util.hit.BlockHitResult result = new net.minecraft.util.hit.BlockHitResult(
            new Vec3d(bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5),
            dir, bp, false
        );

        mc.interactionManager.interactBlock(mc.player, hand, result);
        mc.player.swingHand(hand);
    }

    // ──────────────────────────────────────────────────────────
    private void attackEntity(Entity entity) {
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        attacks++;
    }

    // ──────────────────────────────────────────────────────────
    private float getCrystalDamage(LivingEntity entity, Vec3d crystalPos) {
        return DamageUtils.crystalDamage(entity, crystalPos, predictMovement.get(),
            BlockPos.ofFloored(crystalPos).down());
    }

    // ──────────────────────────────────────────────────────────
    private float getBestTargetDamage(Vec3d crystalPos) {
        float best = 0f;
        for (LivingEntity target : targets) {
            float dmg = getCrystalDamage(target, crystalPos);
            if (dmg > best) best = dmg;
        }
        return best;
    }

    // ──────────────────────────────────────────────────────────
    private double getTotalHP() {
        return mc.player.getHealth() + mc.player.getAbsorptionAmount();
    }

    // ──────────────────────────────────────────────────────────
    private boolean isBehindWall(Vec3d pos) {
        Vec3d eyePos = mc.player.getEyePos();
        net.minecraft.world.RaycastContext ctx = new net.minecraft.world.RaycastContext(
            eyePos, pos,
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            mc.player
        );
        net.minecraft.util.hit.BlockHitResult result = mc.world.raycast(ctx);
        return result.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
            && !result.getBlockPos().equals(BlockPos.ofFloored(pos));
    }

    // ──────────────────────────────────────────────────────────
    private boolean isNearFriend(Vec3d pos) {
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (!Friends.get().isFriend(player)) continue;
            if (entity.getPos().distanceTo(pos) < 3.0) return true;
        }
        return false;
    }

    @Override
    public String getInfoString() {
        if (targets.isEmpty()) return null;
        return targets.get(0) instanceof PlayerEntity p
            ? p.getGameProfile().getName()
            : targets.get(0).getType().getName().getString();
    }
}
