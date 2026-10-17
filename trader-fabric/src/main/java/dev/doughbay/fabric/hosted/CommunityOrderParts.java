package dev.doughbay.fabric.hosted;

import java.util.*;
import java.util.regex.Pattern;

/** Public vanilla item metadata only; unknown/custom text is held back, never scrubbed into another variant. */
final class CommunityOrderParts {
    private static final Pattern ENCHANT = Pattern.compile("(?:Aqua Affinity|Bane of Arthropods|Blast Protection|Breach|Channeling|Curse of Binding|Curse of Vanishing|Density|Depth Strider|Efficiency|Feather Falling|Fire Aspect|Fire Protection|Flame|Fortune|Frost Walker|Impaling|Infinity|Knockback|Looting|Loyalty|Luck of the Sea|Lunge|Lure|Mending|Multishot|Piercing|Power|Projectile Protection|Protection|Punch|Quick Charge|Respiration|Riptide|Sharpness|Silk Touch|Smite|Soul Speed|Sweeping Edge|Swift Sneak|Thorns|Unbreaking|Wind Burst)(?: [IVX]{1,8})?");
    private static final Pattern EFFECT = Pattern.compile("(?:Absorption|Bad Omen|Blindness|Darkness|Dolphin's Grace|Fire Resistance|Glowing|Haste|Health Boost|Hero of the Village|Hunger|Infested|Instant Damage|Instant Health|Invisibility|Jump Boost|Levitation|Luck|Mining Fatigue|Nausea|Night Vision|Oozing|Poison|Raid Omen|Regeneration|Resistance|Saturation|Slow Falling|Slowness|Speed|Strength|Trial Omen|Unluck|Water Breathing|Weakness|Weaving|Wind Charged|Wither)(?: [IVX]{1,8})?(?: \\([0-9]{1,3}:[0-9]{2}(?::[0-9]{2})?\\))?");
    private static final Pattern ATTRIBUTE = Pattern.compile("[+-][0-9]{1,4}(?:\\.[0-9]{1,3})?%? (?:Attack Damage|Attack Speed|Armor|Armor Toughness|Knockback Resistance|Movement Speed|Speed|Safe Fall Distance|Waypoint Transmit Range)");
    private static final Set<String> FIXED = Set.of("Applies to:","Ingredients:","When Applied:","Armor","Diamond Equipment","Ingots & Crystals","Netherite Ingot","Smithing Template","No Effects");
    static List<String> safe(List<String> input) {
        if(input==null||input.isEmpty())return List.of();
        if(input.size()>12)return null;
        var result=new ArrayList<String>();
        for(String raw:input){
            if(raw==null)return null;
            String line=raw.replaceAll("§.","").strip();
            if(line.length()>128||line.isEmpty()||!(ENCHANT.matcher(line).matches()||EFFECT.matcher(line).matches()
                    ||ATTRIBUTE.matcher(line).matches()||FIXED.contains(line)||line.matches("Flight Duration: [1-3]")||line.matches("Bees: [0-3] / 3")))return null;
            result.add(line);
        }
        return List.copyOf(result);
    }
    private CommunityOrderParts(){}
}
