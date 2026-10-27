package dev.doughbay.fabric;

import java.util.HashMap;
import java.util.Map;
/** Spectre numbered 9x9 region layout. Requires real, non-randomized Overworld coordinates. */
public final class RegionMapData {
   public static final int MAP_SIZE = 9;
   public static final int GRID = MAP_SIZE;
   private static final double REGION_SIZE = 50000.0;
   private static final double MAP_OFFSET = 225000.0;
   private static final String[] REGION_TYPE_NAMES = new String[]{"EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"};
   private static final int[] REGION_TYPE_COLORS = new int[]{0xFF606060, 0xFF767676, 0xFF8C8C8C, 0xFFA2A2A2, 0xFFB8B8B8, 0xFFCECECE};
   private static final Map<Integer, RegionMapData.RegionInfo> REGION_MAP = buildRegionMap();

   private RegionMapData() {
   }

   public static RegionMapData.RegionInfo regionInfo(int index) {
      return REGION_MAP.get(index);
   }

   public static int cell(int col, int row) {
      RegionInfo info = col >= 0 && row >= 0 && col < GRID && row < GRID ? regionInfo(row * GRID + col) : null;
      return info == null ? -1 : info.regionId;
   }
   public static String name(int regionId) {
      for (RegionInfo info : REGION_MAP.values()) if (info.regionId == regionId)
         return regionId + " · " + REGION_TYPE_NAMES[info.regionType];
      return "Unknown";
   }
   public static int color(int regionId) {
      for (RegionInfo info : REGION_MAP.values()) if (info.regionId == regionId) return regionColor(info.regionType);
      return 0xFF242424;
   }
   public static int colorByName(String family) {
      for (int i=0; i<REGION_TYPE_NAMES.length; i++) if (REGION_TYPE_NAMES[i].equals(family)) return regionColor(i);
      return 0xFF242424;
   }

   public static String command(int regionId) {
      for (RegionInfo info : REGION_MAP.values()) if (info.regionId == regionId)
         return switch (info.regionType) {
            case 0 -> "eu central";
            case 1 -> "eu west";
            case 2 -> "east";
            case 3 -> "west";
            case 4 -> "asia";
            case 5 -> "oceania";
            default -> "";
         };
      return "";
   }

   public static int regionColor(int type) {
      return type >= 0 && type < REGION_TYPE_COLORS.length ? REGION_TYPE_COLORS[type] : -7106930;
   }

   public static String[] regionTypeNames() {
      return REGION_TYPE_NAMES;
   }

   public static int[] regionTypeColors() {
      return REGION_TYPE_COLORS;
   }

   public static int regionAt(double x, double z) {
      if (!Double.isFinite(x) || !Double.isFinite(z)) return -1;
      int[] grid = worldToGrid(x, z);
      if (grid[0] >= 0 && grid[0] < 9 && grid[1] >= 0 && grid[1] < 9) {
         RegionMapData.RegionInfo info = REGION_MAP.get(grid[1] * 9 + grid[0]);
         return info != null ? info.regionId : -1;
      } else {
         return -1;
      }
   }

   public static String regionTypeName(double x, double z) {
      int[] grid = worldToGrid(x, z);
      if (grid[0] >= 0 && grid[0] < 9 && grid[1] >= 0 && grid[1] < 9) {
         RegionMapData.RegionInfo info = REGION_MAP.get(grid[1] * 9 + grid[0]);
         return info == null ? "Unknown" : REGION_TYPE_NAMES[info.regionType];
      } else {
         return "Unknown";
      }
   }

   public static int[] worldToGrid(double x, double z) {
      if (!Double.isFinite(x) || !Double.isFinite(z)) return new int[]{-1, -1};
      return new int[]{(int)Math.floor((x + 225000.0) / 50000.0), (int)Math.floor((z + 225000.0) / 50000.0)};
   }

   public static double[] worldToCellPosition(double x, double z) {
      return new double[]{positiveModulo(x + 225000.0, 50000.0) / 50000.0, positiveModulo(z + 225000.0, 50000.0) / 50000.0};
   }

   private static double positiveModulo(double value, double divisor) {
      double mod = value % divisor;
      return mod < 0.0 ? mod + divisor : mod;
   }

   private static Map<Integer, RegionMapData.RegionInfo> buildRegionMap() {
      int[][] layout = new int[][]{
         {82, 5},
         {100, 3},
         {101, 3},
         {102, 3},
         {103, 2},
         {104, 2},
         {105, 2},
         {106, 2},
         {91, 2},
         {83, 5},
         {44, 3},
         {75, 3},
         {42, 3},
         {41, 2},
         {40, 2},
         {39, 2},
         {38, 2},
         {92, 2},
         {84, 5},
         {45, 3},
         {14, 3},
         {13, 3},
         {12, 2},
         {11, 2},
         {10, 2},
         {37, 2},
         {93, 2},
         {85, 5},
         {46, 5},
         {74, 5},
         {3, 3},
         {2, 2},
         {1, 2},
         {25, 2},
         {36, 2},
         {94, 2},
         {86, 4},
         {47, 4},
         {72, 4},
         {71, 4},
         {5, 2},
         {4, 2},
         {24, 2},
         {35, 2},
         {95, 2},
         {87, 4},
         {51, 1},
         {17, 1},
         {9, 0},
         {8, 0},
         {7, 0},
         {23, 0},
         {34, 0},
         {96, 2},
         {88, 4},
         {54, 1},
         {18, 1},
         {61, 0},
         {62, 0},
         {21, 0},
         {22, 0},
         {33, 0},
         {97, 0},
         {89, 0},
         {26, 1},
         {27, 0},
         {28, 0},
         {29, 0},
         {30, 0},
         {59, 0},
         {32, 0},
         {98, 0},
         {90, 0},
         {107, 1},
         {108, 1},
         {109, 1},
         {110, 1},
         {111, 1},
         {112, 1},
         {113, 1},
         {99, 0}
      };
      Map<Integer, RegionMapData.RegionInfo> map = new HashMap<>();

      for (int i = 0; i < layout.length; i++) {
         map.put(i, new RegionMapData.RegionInfo(layout[i][0], layout[i][1]));
      }

      return map;
   }
   public static final class RegionInfo {
      public final int regionId;
      public final int regionType;

      RegionInfo(int regionId, int regionType) {
         this.regionId = regionId;
         this.regionType = regionType;
      }
   }
}
