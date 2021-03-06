package me.aztl.azutoru.ability.water.ice.combo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.ComboAbility;
import com.projectkorra.projectkorra.ability.ElementalAbility;
import com.projectkorra.projectkorra.ability.IceAbility;
import com.projectkorra.projectkorra.ability.util.ComboManager.AbilityInformation;
import com.projectkorra.projectkorra.util.BlockSource;
import com.projectkorra.projectkorra.util.ClickType;
import com.projectkorra.projectkorra.util.TempBlock;
import com.projectkorra.projectkorra.waterbending.Torrent;
import com.projectkorra.projectkorra.waterbending.ice.IceSpikeBlast;
import com.projectkorra.projectkorra.waterbending.plant.PlantRegrowth;

import me.aztl.azutoru.Azutoru;
import me.aztl.azutoru.AzutoruMethods;
import me.aztl.azutoru.ability.util.Shot;

public class IceShots extends IceAbility implements AddonAbility, ComboAbility {

	public static enum AnimateState {
		RISE, TOWARD_PLAYER, CIRCLE;
	}
	
	private long cooldown, duration;
	private double sourceRange, range, damage, hitRadius, ringRadius, speed;
	private int maxIceShots;
	
	private Block sourceBlock;
	private Location location, eyeLoc;
	private Vector direction;
	private AnimateState animation;
	private ConcurrentHashMap<Block, TempBlock> affectedBlocks;
	private boolean decayableSource;
	
	public IceShots(Player player) {
		super(player);
		
		IceSpikeBlast iceSpike = getAbility(player, IceSpikeBlast.class);
		if (iceSpike != null) {
			iceSpike.remove();
		}
		
		if (!bPlayer.canBendIgnoreBinds(this)) {
			return;
		}
		
		cooldown = Azutoru.az.getConfig().getLong("Abilities.Water.IceShots.Cooldown");
		duration = Azutoru.az.getConfig().getLong("Abilities.Water.IceShots.Duration");
		sourceRange = Azutoru.az.getConfig().getDouble("Abilities.Water.IceShots.SourceRange");
		maxIceShots = Azutoru.az.getConfig().getInt("Abilities.Water.IceShots.MaxIceShots");
		range = Azutoru.az.getConfig().getDouble("Abilities.Water.IceShots.Range");
		damage = Azutoru.az.getConfig().getDouble("Abilities.Water.IceShots.DamagePerShot");
		hitRadius = Azutoru.az.getConfig().getDouble("Abilities.Water.IceShots.HitRadius");
		ringRadius = Azutoru.az.getConfig().getDouble("Abilities.Water.IceShots.RingRadius");
		speed = Azutoru.az.getConfig().getDouble("Abilities.Water.IceShots.Speed");
		
		applyModifiers();
		
		animation = AnimateState.RISE;
		affectedBlocks = new ConcurrentHashMap<>();
		
		sourceBlock = BlockSource.getWaterSourceBlock(player, sourceRange, ClickType.SHIFT_DOWN, true, true, true, true, true);
		if (sourceBlock != null && !GeneralMethods.isRegionProtectedFromBuild(this, sourceBlock.getLocation())) {
			location = sourceBlock.getLocation().clone();
			if (isDecayablePlant(sourceBlock)) {
				decayableSource = true;
				location = sourceBlock.getRelative(BlockFace.UP).getLocation().clone();
			}
			start();
		}
	}
	
	private void applyModifiers() {
		if (isNight(player.getWorld())) {
			cooldown -= ((long) getNightFactor(cooldown) - cooldown);
			duration = (long) getNightFactor(duration);
		}
		
		if (bPlayer.isAvatarState()) {
			cooldown /= 2;
			duration *= 2;
			range *= 1.25;
			damage *= 1.25;
			speed *= 1.25;
			maxIceShots += 2;
		}
	}
	
	@Override
	public void progress() {
		if (isPlant(sourceBlock) || isSnow(sourceBlock)) {
			if (!isDecayablePlant(sourceBlock)) {
				sourceBlock.setType(Material.AIR);
			}
			new PlantRegrowth(player, sourceBlock, 2);
		}
		
		if (TempBlock.isTempBlock(sourceBlock)) {
			TempBlock tb = TempBlock.get(sourceBlock);
			if (Torrent.getFrozenBlocks().containsKey(tb)) {
				Torrent.massThaw(tb);
			} else if (!isBendableWaterTempBlock(tb) && !decayableSource) {
				remove(false);
				return;
			}
		}
		
		if (!bPlayer.getBoundAbilityName().equalsIgnoreCase("icespike")) {
			remove(true);
			return;
		}
		
		if (!player.isSneaking()) {
			remove(true);
			return;
		}
		
		if (duration > 0 && System.currentTimeMillis() > getStartTime() + duration) {
			remove(true);
			return;
		}
		
		if (maxIceShots < 1 && !hasAbility(player, Shot.class)) {
			remove(true);
			return;
		}
		
		if (direction == null) {
			direction = player.getEyeLocation().getDirection();
		}
		
		eyeLoc = player.getTargetBlock((HashSet<Material>) null, 2).getLocation();
		eyeLoc.setY(player.getEyeLocation().getY());
		
		if (animation == AnimateState.RISE && location != null) {
			AzutoruMethods.revertBlocks(affectedBlocks);
			location.add(0, 1, 0);
			Block block = location.getBlock();
			
			if (!(isWaterbendable(block) || ElementalAbility.isAir(block.getType()))
					|| GeneralMethods.isRegionProtectedFromBuild(this, block.getLocation())) {
				remove(false);
				return;
			}
			
			createBlock(block, Material.WATER);
			if (location.distanceSquared(sourceBlock.getLocation()) > 4) {
				animation = AnimateState.TOWARD_PLAYER;
			}
			
		} else if (animation == AnimateState.TOWARD_PLAYER) {
			AzutoruMethods.revertBlocks(affectedBlocks);
			Vector vec = GeneralMethods.getDirection(location, eyeLoc);
			location.add(vec.normalize());
			Block block = location.getBlock();
			
			if (!(isWaterbendable(block) || ElementalAbility.isAir(block.getType())
					|| GeneralMethods.isRegionProtectedFromBuild(this, block.getLocation()))) {
				remove(false);
				return;
			}
			
			createBlock(block, Material.WATER);
			if (location.distanceSquared(eyeLoc) < 2) {
				animation = AnimateState.CIRCLE;
				Vector tempDir = player.getLocation().getDirection();
				tempDir.setY(0);
				direction = tempDir.normalize();
				AzutoruMethods.revertBlocks(affectedBlocks);
			}
		} else if (animation == AnimateState.CIRCLE) {
			drawCircle(120, 5);
		}
	}
	
	public void drawCircle(double theta, double increment) {
		double rotateSpeed = 45;
		AzutoruMethods.revertBlocks(affectedBlocks);
		direction = GeneralMethods.rotateXZ(direction, rotateSpeed);
		for (double i = 0; i < theta; i += increment) {
			Vector dir = GeneralMethods.rotateXZ(direction, i - theta / 2).normalize().multiply(ringRadius);
			dir.setY(0);
			Block block = player.getEyeLocation().add(dir).getBlock();
			location = block.getLocation();
			if (ElementalAbility.isAir(block.getType()) && !GeneralMethods.isRegionProtectedFromBuild(this, block.getLocation())) {
				createBlock(block, Material.WATER);
			}
		}
	}
	
	public void onClick() {
		if (animation == AnimateState.CIRCLE && maxIceShots > 0) {
			new Shot(player, this, eyeLoc, player.getEyeLocation().getDirection(), damage, range, hitRadius, speed, false);
			maxIceShots--;
		}
	}
	
	public void createBlock(Block block, Material material) {
		affectedBlocks.put(block, new TempBlock(block, material));
	}
	
	public void remove(boolean addCooldown) {
		super.remove();
		AzutoruMethods.revertBlocks(affectedBlocks);
		affectedBlocks.clear();
		if (addCooldown) {
			bPlayer.addCooldown(this);
		}
		return;
	}
	
	public Integer getMaxIceShots() {
		return maxIceShots;
	}
	
	public void setMaxIceShots(int maxIceShots) {
		this.maxIceShots = maxIceShots;
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public Location getLocation() {
		return location;
	}

	@Override
	public String getName() {
		return "IceShots";
	}
	
	@Override
	public String getDescription() {
		return "This combo allows a waterbender to form a ring of water around themselves, much like Torrent, and shoot bullets of ice from the ring.";
	}
	
	@Override
	public String getInstructions() {
		return "Torrent (Hold sneak) > IceSpike (Release sneak) > IceSpike (Hold sneak) > IceSpike (Left-click multiple times)";
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public Object createNewComboInstance(Player player) {
		return new IceShots(player);
	}

	@Override
	public ArrayList<AbilityInformation> getCombination() {
		ArrayList<AbilityInformation> combo = new ArrayList<>();
		combo.add(new AbilityInformation("Torrent", ClickType.SHIFT_DOWN));
		combo.add(new AbilityInformation("IceSpike", ClickType.SHIFT_UP));
		combo.add(new AbilityInformation("IceSpike", ClickType.SHIFT_DOWN));
		return combo;
	}

	@Override
	public String getAuthor() {
		return Azutoru.az.dev();
	}

	@Override
	public String getVersion() {
		return Azutoru.az.version();
	}

	@Override
	public void load() {
	}

	@Override
	public void stop() {
	}
	
	@Override
	public boolean isEnabled() {
		return true;
	}

}
