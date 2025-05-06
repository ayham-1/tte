/*
 *
 *                ACHTUNG/WARNING
 *         THIS IS NOT TO BE DESTRIBUTED
 *           IF YOU RECEIVED THIS FROM
 *               Ayham  Aboualfadl
 *     THROUGH ANY OF HIS COMMUNICATION MEANS,
 *     YOU ARE NOT ALLOWED TO FURTHER DISTRIBUTE
 *
 *
 *             Only valid throughout
 *               the AuD Competition
 *           After that, licensed under
 *                   GPL v3.0
 *
 *  :D
 * */

import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import page.codeberg.terratactician_expandoria.*;
import page.codeberg.terratactician_expandoria.bots.*;
import page.codeberg.terratactician_expandoria.world.CubeCoordinate;
import page.codeberg.terratactician_expandoria.world.Metrics;
import page.codeberg.terratactician_expandoria.world.tiles.*;
import page.codeberg.terratactician_expandoria.world.tiles.Tile.TileType;

/*
 * Observations:
 *  - only one placement of a tile is allowed per turn
 *  - memory is more abundant than time!
 *  - the longer a tile is placed the better,
 *  - more tiles placed earlier == better
 *  - marketplaces convert food/material to money, and are actually very
 * important mid-game
 *  - after placing, the getMap() does not update
 *
 * Current:
 *  -
 *
 * TODOs:
 *  - make groups that have single concrete tile and other 'virtual' ones
 *    - windmill
 *    - beehives
 *    - marketplaces
 *  - investigate sectors
 *    - what functions should sectors offer:
 *    - find a way to optimize multiple group placements
 *    - wheat/forest should also take in mind changes of score of other stuff
 * like beehives, windmills and marketplaces
 *
 *  - quarries should place themselves when no available stones with empty
 * possibility
 *  - stones should pick beside quarries, keeping in mind empty spaces for their
 * quarries
 *  - give more value for higher resources growth available when placing
 * marketplaces
 *  - solve housing crisis
 *  - find a better marketplace calculation
 *
 *  - maoi effect don't stack
 *  - new forest (also groups) should prefer to circle beehives
 *
 *
 *  - avoid beehives closing forests too much
 *
 *
 * */

public class MyBot extends ChallengeBot {
  static final boolean PRINT_DEBUG = false;

  @Override
  public int getMatrikel() {
    return 251435;
  }

  @Override
  public String getStudentName() {
    return "Ayham A.F.";
  }

  @Override
  public String getName() {
    return "Bob the Bot the Builder";
  }

  abstract class Group {
    MyBot bot = null;

    HashMap<CubeCoordinate, TileType> tiles = new HashMap<>();
    Set<CubeCoordinate> coords_placable = new LinkedHashSet<>();
    double last_score = 0.0f;

    Group(MyBot bot) { this.bot = bot; }

    ResourceType res_type;

    abstract double calc_score();
    abstract double calc_new_score(TileType newtype, CubeCoordinate coord);

    abstract boolean accepts(TileType type);
    abstract boolean fine_neighbor(TileType type);
    abstract void add_tile(TileType type);

    public void add_tile(TileType type, CubeCoordinate coord) throws Exception {
      if (!this.bot.place_tile(type, coord))
        throw new Exception("place_tile() failed");

      this.tiles.put(coord, type);

      if (this.coords_placable.contains(coord))
        this.coords_placable.remove(coord);

      for (var cring : coord.getRing(1)) {
        if (world.getMap().at(cring) == null &&
            world.getBuildArea().contains(cring))
          this.coords_placable.add(cring);
      }
    }

    public void update_coords_placable() { this.update_coords_placable(null); }

    public void update_coords_placable(CubeCoordinate coord) {
      this.coords_placable = new LinkedHashSet<>();
      for (var c : tiles.keySet()) {
        for (var cring : c.getRing(1)) {
          if (!cring.equals(coord) &&
              this.bot.world.getMap().at(cring) == null &&
              this.bot.world.getBuildArea().contains(cring)) {
            this.coords_placable.add(cring);
          }
        }
      }
    }

    public boolean addable() { return !this.coords_placable.isEmpty(); }
  }

  class WheatGroup extends Group {
    /* Represents a Wheat group, optimal means:
     * - have 9 connected Wheat Tiles
     * - group members all close together (really?)
     **/

    WheatGroup(MyBot bot, CubeCoordinate new_coord) throws Exception {
      super(bot);
      super.res_type = ResourceType.Food;
    }

    @Override
    public double calc_score() {
      int w = this.tiles.size();
      double s = (w <= 9) ? 4.0f : -2.5f;
      return Math.pow(Math.E, -Math.pow(((w - 9.0f) / s), 2.0f)) * 2.4f + 0.1f;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      int w = this.tiles.size() + 1;
      double s = (w <= 9) ? 4.0f : -2.5f;
      return Math.pow(Math.E, -Math.pow(((w - 9.0f) / s), 2.0f)) * 2.4f + 0.1f;
    }

    @Override
    public void add_tile(TileType type) {
      CubeCoordinate best_cplacable = null;
      int low_avoid = Integer.MAX_VALUE;
      int best_packing = 0;

      for (var cplacable : this.coords_placable) {
        boolean has_foreign = false;
        int packing = 0;
        for (var cring : cplacable.getRing(1)) {
          var t = this.bot.world.getMap().at(cring);
          if (this.tiles.containsKey(cring))
            packing++;
          else if (t != null && t.getTileType() == TileType.Wheat) {
            has_foreign = true;
            break;
          }
        }

        // avoid joining with other groups
        if (has_foreign)
          continue;

        int avoid = this.bot.avoid_coord_for_other_group(TileType.Wheat,
                                                         cplacable, null);

        if (packing >= best_packing && avoid <= low_avoid) {
          best_packing = packing;
          low_avoid = avoid;
          best_cplacable = cplacable;
        }
      }
      try {
        this.add_tile(type, best_cplacable);
      } catch (Exception e) {
      }
    }

    @Override
    public boolean addable() {
      boolean o = super.addable();

      // refuse making groups larger than 8
      if (this.tiles.size() >= 9)
        return false;

      for (var cplacable : this.coords_placable) {
        boolean could_be_placable = true;
        for (var cring : cplacable.getRing(1)) {
          var t = this.bot.world.getMap().at(cring);
          if (t == null || this.tiles.containsKey(cring))
            continue;

          if (t.getTileType() == TileType.Wheat) {
            could_be_placable = false;
            break;
          }
        }

        if (could_be_placable)
          return o;
      }

      return false;
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Wheat;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type == TileType.Beehive || type == TileType.Windmill ||
          type == TileType.Marketplace;
    }
  }

  class ForestGroup extends Group {
    /* Represents a forest group, optimal means:
     * - most neighboring forest tiles
     * - group members all close together (implied)
     **/

    ForestGroup(MyBot bot, CubeCoordinate new_coord) throws Exception {
      super(bot);
    }

    @Override
    public double calc_score() {
      double score = 0;
      for (var c : this.tiles.keySet()) {
        for (var cring : c.getRing(1)) {
          if (this.tiles.containsKey(cring))
            score++;
        }
      }
      return score * 0.5f + 0.25;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      double score = this.calc_score();
      for (var cring : coord.getRing(1)) {
        if (this.tiles.containsKey(cring))
          score++;
      }

      return score;
    }

    @Override
    public void add_tile(TileType type) {
      CubeCoordinate best_cplacable = null;
      int best_packing = 0;
      int low_avoid = Integer.MAX_VALUE;

      for (var cplacable : this.coords_placable) {
        if (best_cplacable == null)
          best_cplacable = cplacable;

        int packing = 0;
        for (var cring : cplacable.getRing(1)) {
          if (this.tiles.containsKey(cring))
            packing++;
        }

        int avoid = this.bot.avoid_coord_for_other_group(TileType.Forest,
                                                         cplacable, this);

        if (packing >= best_packing && avoid <= low_avoid) {
          best_packing = packing;
          low_avoid = avoid;
          best_cplacable = cplacable;
        }
      }
      try {
        this.add_tile(type, best_cplacable);
      } catch (Exception e) {
      }
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Forest;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type == TileType.Beehive || type == TileType.Marketplace;
    }
  }

  class HousingGroup extends Group {
    /* Represents a housing group, optimal means:
     * - double houses have exactly three neighbors
     * - three small houses
     **/

    HousingGroup(MyBot bot, TileType type, CubeCoordinate new_coord)
        throws Exception {
      super(bot);
    }

    @Override
    public double calc_score() {
      double sscore = 0;
      double dscore = 0;

      for (var t : this.tiles.entrySet()) {
        double boost = 0.0f;
        // TODO(ayham-1): put this in grouping
        for (var cring : t.getKey().getArea(3)) {
          var crt = this.bot.world.getMap().at(cring);
          if (crt == null)
            continue;
          double current_boost = 0.0f;
          if (crt.getTileType() == TileType.Moai) {
            Set<TileType> unique = new LinkedHashSet<>();
            for (var cneighbor : cring.getArea(4)) {
              var un = this.bot.world.getMap().at(cneighbor);
              if (un != null)
                unique.add(un.getTileType());
            }
            current_boost = 1 + 0.5f * Math.max(0, unique.size() - 2);
            if (current_boost >= boost)
              boost = current_boost;
          }
        }

        if (t.getValue() == TileType.SmallHouse) {
          sscore += 2.0f + (2.0f * boost);
        } else if (t.getValue() == TileType.DoubleHouse) {

          int count = 0;
          for (var cring : t.getKey().getRing(1)) {
            if (this.tiles.containsKey(cring))
              count++;
          }
          dscore +=
              (((-Math.abs(count - 3) + 3) / (2.0f / 3.0f)) + 0.5f) * boost;
        }
      }

      return sscore + dscore;
    }

    @Override
    public double calc_new_score(TileType newtype, CubeCoordinate coord) {
      // add coord to tiles temp.
      this.tiles.put(coord, newtype);

      double score = this.calc_score();

      this.tiles.remove(coord);

      return score;
    }

    @Override
    public void add_tile(TileType type) {
      // TODO(ayham-1): place correctly according to score
      if (type == TileType.SmallHouse) {
        CubeCoordinate best_cplacable = null;
        int best_packing = 0;
        int low_avoid = Integer.MAX_VALUE;
        for (var cplacable : this.coords_placable) {
          if (best_cplacable == null)
            best_cplacable = cplacable;

          int packing = 0;
          for (var cring : cplacable.getRing(1)) {
            if (this.tiles.containsKey(cring))
              packing++;
          }

          int avoid = this.bot.avoid_coord_for_other_group(TileType.SmallHouse,
                                                           cplacable, this);

          if (packing >= best_packing && avoid <= low_avoid) {
            best_packing = packing;
            low_avoid = avoid;
            best_cplacable = cplacable;
          }
        }
        try {
          this.add_tile(TileType.SmallHouse, best_cplacable);
        } catch (Exception e) {
        }
      } else if (type == TileType.DoubleHouse) {
        CubeCoordinate best_cplacable = null;
        int low_avoid = Integer.MAX_VALUE;
        int best_count = 0;
        for (var cplacable : this.coords_placable) {
          if (best_cplacable == null)
            best_cplacable = cplacable;

          int count = 0;
          boolean use = true;
          for (var cring : cplacable.getRing(1)) {
            var t = this.bot.world.getMap().at(cring);
            if (this.tiles.containsKey(cring) &&
                this.tiles.get(cring) == TileType.SmallHouse)
              count++;
            else if (t != null) {
              if (t.getTileType() == TileType.SmallHouse ||
                  t.getTileType() == TileType.DoubleHouse)
                use = false;
              break;
            }
          }
          if (!use)
            continue;

          int avoid = this.bot.avoid_coord_for_other_group(TileType.DoubleHouse,
                                                           cplacable, this);

          if (avoid <= low_avoid && best_count <= 3 && count >= best_count) {
            low_avoid = avoid;
            best_count = count;
            best_cplacable = cplacable;
          }
        }
        try {
          this.add_tile(TileType.DoubleHouse, best_cplacable);
        } catch (Exception e) {
        }
      }
    }

    @Override
    public boolean accepts(TileType type) {
      double sscore = 0;
      double dscore = 0;
      int dcount = 0;

      for (var t : this.tiles.entrySet()) {
        if (t.getValue() == TileType.SmallHouse)
          sscore++;
        else if (t.getValue() == TileType.DoubleHouse) {
          int count = 0;
          for (var cring : t.getKey().getRing(1)) {
            if (this.tiles.containsKey(cring))
              count++;
          }
          dscore += (count == 3) ? 1 : 0;
          dcount++;
        }
      }
      if (type == TileType.SmallHouse && sscore < 3)
        return true;
      if (type == TileType.DoubleHouse && dscore <= 1)
        return true;
      return false;
    }

    @Override
    public boolean fine_neighbor(TileType type) {
      return type == TileType.Moai || type == TileType.Marketplace;
    }
  }

  public enum ResourceType { Food, Materials, Money }

  class PlacementSuggestion implements Comparable<PlacementSuggestion> {
    CubeCoordinate coord = null;
    double delta_growth = 0.0f;
    TileType tile_type;
    Group group = null;

    PlacementSuggestion(TileType typ, CubeCoordinate coord, double delta,
                        Group group) {
      this.tile_type = typ;
      this.coord = coord;
      this.delta_growth = delta;
      this.group = group;
    }

    @Override
    public int compareTo(PlacementSuggestion other) {
      return Double.compare(this.delta_growth, other.delta_growth);
    }
  };

  // API vars
  World world;
  Controller controller;

  //// state vars
  int round = 0;
  boolean is_first = true;
  boolean must_win = false;
  boolean redrawn = false;

  /* stores all groups that are tracked on the map,
   * there is no garantue that all tiles are in a group,
   * or that each tile appears in a single group.
   * */
  ArrayList<Group> groups = new ArrayList<>();

  /* placable coords that are not-isolated, manually updated every placed
   * tile and every round change */
  Set<CubeCoordinate> coords_placable = new LinkedHashSet<>();
  HashMap<CubeCoordinate, Boolean> marketplaces = new HashMap<>();

  /* per hand suggestions per resource type */
  Set<PlacementSuggestion> suggests_food = new LinkedHashSet<>();
  Set<PlacementSuggestion> suggests_materials = new LinkedHashSet<>();
  Set<PlacementSuggestion> suggests_money = new LinkedHashSet<>();
  Set<PlacementSuggestion> suggests_grass = new LinkedHashSet<>(); // ugh

  Metrics resource_current = new Metrics();
  Metrics resource_target = new Metrics();
  Metrics resource_growth = new Metrics();

  // per entire hand delta growth change
  Metrics resource_growth_delta = new Metrics();
  Metrics resource_growth_last = new Metrics();
  int resource_delta_count = 0;
  boolean growth_last_updated = false;

  double round_time_left = 0.0f;

  boolean reachable_money = false;
  boolean reachable_food = false;
  boolean reachable_materials = false;

  double resource_perc_money = 0;
  double resource_perc_food = 0;
  double resource_perc_materials = 0;

  boolean event_empty_hand_start = false;

  @Override
  public void executeTurn(World world, Controller controller) {
    this.world = world;
    this.controller = controller;

    // track lives
    if (this.round != world.getRound()) {
      this.round = world.getRound();
      if (!this.reachable_money || !this.reachable_food ||
          !this.reachable_materials)
        this.must_win = true;
      else {
        this.must_win = false;
        this.redrawn = false;
      }
      this.update_coords_placable();
    }

    // calculate per hand growth delta
    if (!this.world.getHand().isEmpty()) {
      this.resource_growth_last = this.resource_growth;
      growth_last_updated = true;
    } else if (this.world.getHand().isEmpty() && growth_last_updated) {
      this.resource_growth_delta.money +=
          this.resource_growth.money - this.resource_growth_last.money;

      this.resource_growth_delta.food +=
          this.resource_growth.food - this.resource_growth_last.food;

      this.resource_growth_delta.materials +=
          this.resource_growth.materials - this.resource_growth_last.materials;
      this.resource_delta_count++;
      growth_last_updated = false;
    }

    this.update_resources_stat();

    // always redraw when free
    if (world.getHand().isEmpty() && world.getRedrawTime() <= 0) {
      controller.redraw();
      return;
    }

    // always collect rewards no matter what
    for (var reward : world.getRewards()) {
      controller.collectReward(reward.getCoordinate());
      if (!controller.actionPossible())
        break;
    }
    if (!controller.actionPossible())
      return;

    // force update marketplaces when hand is empty
    if (this.world.getHand().isEmpty()) {
      if (!this.marketplaces.values().contains(false)) // lol maybe use variable
        for (var market : this.marketplaces.keySet())
          this.marketplaces.put(market, false);

      // only update marketplaces if hand is empty, don't block placements
      this.setup_marketplaces();
    }

    if (!this.controller.actionPossible())
      return;

    if (world.getHand().isEmpty()) {
      // determine if we need to redraw since we are not hitting the target
      // resources [CURRENTLY DISABLED]
      var cost = world.getRedrawCosts();
      boolean redrawable = this.resource_current.money >= cost.money &&
                           this.resource_current.food >= cost.food &&
                           this.resource_current.materials >= cost.materials;
      if (redrawable) {
        if ((!this.reachable_money || !this.reachable_food ||
             !this.reachable_materials) &&
            this.must_win) {
          this.redrawn = true;
        } else {
          double money = this.resource_current.money +
                         this.resource_growth.money * this.round_time_left;

          double food = this.resource_current.food +
                        this.resource_growth.food * this.round_time_left;

          double mat = this.resource_current.materials +
                       this.resource_growth.materials * this.round_time_left;

          double offset_m =
              (this.resource_growth_delta.money / this.resource_delta_count) *
              this.round_time_left;

          double offset_f =
              (this.resource_growth_delta.food / this.resource_delta_count) *
              this.round_time_left;

          double offset_mat = (this.resource_growth_delta.materials /
                               this.resource_delta_count) *
                              this.round_time_left;

          double early_bias =
              1 -
              (Math.pow(Math.E,
                        -((this.round + (this.world.getRoundTime() / 60.0f)))));

          if ((money - cost.money) + offset_m >
                  this.resource_target.money * early_bias &&
              (food - cost.food) + offset_f >
                  this.resource_target.food * early_bias &&
              (mat - cost.materials) + offset_mat >
                  this.resource_target.materials * early_bias)
            if (this.coords_placable.size() >= 5) // don't have extra on hand
              controller.redraw();
        }
      }
    }

    // make world zero as first tile
    if (is_first) {
      this.coords_placable.add(new CubeCoordinate());
      is_first = false;
    }

    // TODO(ayham-1): clear on empty hand, and remove suggestions when done
    this.suggests_food.clear();
    this.suggests_money.clear();
    this.suggests_materials.clear();
    this.suggests_grass.clear(); // ugh 3

    for (var card : world.getHand()) {
      if (!controller.actionPossible())
        return;

      if (card == TileType.Marketplace) {
        this.place_marketplace();
      } else if (card == TileType.Grass) {
        this.place_grass();
      } else if (card == TileType.StoneHill) {
        this.place_stonehill();
      } else if (card == TileType.StoneMountain) {
        this.place_stonemountain();
      } else if (card == TileType.StoneRocks) {
        this.place_stonerocks();
      } else if (card == TileType.Wheat) {
        this.place_wheat();
      } else if (card == TileType.Forest) {
        this.place_forest();
      } else if (card == TileType.DoubleHouse) {
        this.place_doublehouse();
      } else if (card == TileType.SmallHouse) {
        this.place_smallhouse();
      } else if (card == TileType.Windmill) {
        this.place_windmill();
      } else if (card == TileType.Beehive) {
        this.place_beehive();
      } else if (card == TileType.StoneQuarry) {
        this.place_stonequarry();
      } else if (card == TileType.Moai) {
        this.place_moai();
      }
    }

    Set<PlacementSuggestion> suggestions_most = null;
    Set<PlacementSuggestion> suggestions_mid = null;
    Set<PlacementSuggestion> suggestions_least = null;
    if (this.resource_growth.money <= this.resource_growth.food &&
        this.resource_growth.money <= this.resource_growth.materials) {
      suggestions_most = this.suggests_money;
      if (this.resource_growth.food <= this.resource_growth.materials) {
        suggestions_mid = this.suggests_food;
        suggestions_least = this.suggests_materials;
      } else {
        suggestions_mid = this.suggests_materials;
        suggestions_least = this.suggests_food;
      }
    } else if (this.resource_growth.food <= this.resource_growth.money &&
               this.resource_growth.food <= this.resource_growth.materials) {
      suggestions_most = this.suggests_food;
      if (this.resource_growth.money <= this.resource_growth.materials) {
        suggestions_mid = this.suggests_money;
        suggestions_least = this.suggests_materials;
      } else {
        suggestions_mid = this.suggests_materials;
        suggestions_least = this.suggests_money;
      }
    } else if (this.resource_growth.materials <= this.resource_growth.money &&
               this.resource_growth.materials <= this.resource_growth.food) {
      suggestions_most = this.suggests_materials;
      if (this.resource_growth.money <= this.resource_growth.food) {
        suggestions_mid = this.suggests_money;
        suggestions_least = this.suggests_food;
      } else {
        suggestions_mid = this.suggests_food;
        suggestions_least = this.suggests_money;
      }
    }

    if (!this.controller.actionPossible())
      return;

    this.process_suggestions(suggestions_most);
    suggestions_most.clear();

    if (!this.controller.actionPossible())
      return;

    this.process_suggestions(suggestions_mid);
    suggestions_mid.clear();

    if (!this.controller.actionPossible())
      return;

    this.process_suggestions(suggestions_least);
    suggestions_least.clear();

    if (!this.controller.actionPossible())
      return;

    this.process_suggestions(this.suggests_grass); // ugh 2
  }

  void process_suggestions(Set<PlacementSuggestion> suggestions_group) {
    if (!suggestions_group.iterator().hasNext())
      return;

    List<PlacementSuggestion> list = new ArrayList<>(suggestions_group);
    Collections.sort(list);

    PlacementSuggestion suggest = list.getLast();
    try {
      if (suggest.coord != null && suggest.group != null) {
        // new group
        suggest.group.add_tile(suggest.tile_type, suggest.coord);
        this.groups.add(suggest.group);
      } else if (suggest.coord == null && suggest.group != null) {
        // use already existing group
        suggest.group.add_tile(suggest.tile_type);
      } else if (suggest.coord != null && suggest.group == null) {
        // place without tile
        this.place_tile(suggest.tile_type, suggest.coord);

        // TODO(ayham-1): if marketplace put in groups, handle it there
        if (suggest.tile_type == TileType.Marketplace)
          this.marketplaces.put(suggest.coord, false);
      } else {
        System.out.println("Incorrect configuration of suggestion picking");
      }

    } catch (Exception e) {
      System.out.println("Exception: " + e.toString());
    }
  };

  void place_marketplace() {
    double f_rate = this.market_get_food_rate();
    double m_rate = this.market_get_materials_rate();

    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    double max_delta_score = 0.0f;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int h = 0;
      int d = 0;
      for (var cring : cplacable.getRing(1)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;

        if (t.getTileType() == TileType.SmallHouse)
          h++;
        if (t.getTileType() == TileType.DoubleHouse)
          d++;
      }
      double e = Math.min(1, (h + (d * 2) * 0.4f + 0.2f));

      // TODO(ayham-1): make sector, and calculate from it
      double f = 1.0f;
      double m = 1.0f;

      double dscore = (f * f_rate + m * m_rate) * e;

      int avoid = this.avoid_coord_for_other_group(TileType.Marketplace,
                                                   cplacable, null);
      if (avoid <= low_avoid && dscore >= max_delta_score) {
        low_avoid = avoid;
        max_delta_score = dscore;
        best_cplacable = cplacable;
      }
    }

    if (best_cplacable != null) {
      this.suggests_money.add(new PlacementSuggestion(TileType.Marketplace,
                                                      best_cplacable, 0, null));
    }
  }

  void place_grass() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid =
          this.avoid_coord_for_other_group(TileType.Grass, cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
    this.suggests_grass.add(
        new PlacementSuggestion(TileType.Grass, best_cplacable, 0.0f, null));
  }

  void place_stonehill() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid =
          this.avoid_coord_for_other_group(TileType.StoneHill, cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }

    this.suggests_materials.add(new PlacementSuggestion(
        TileType.StoneHill, best_cplacable, 0.0f, null));
  }

  void place_stonemountain() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid = this.avoid_coord_for_other_group(TileType.StoneMountain,
                                                   cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }

    this.suggests_materials.add(new PlacementSuggestion(
        TileType.StoneMountain, best_cplacable, 0.0f, null));
  }

  void place_stonerocks() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      int avoid = this.avoid_coord_for_other_group(TileType.StoneRocks,
                                                   cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }

    this.suggests_materials.add(new PlacementSuggestion(
        TileType.StoneRocks, best_cplacable, 0.0f, null));
  }

  void place_wheat() {
    // search for a group that accepts wheat
    double max_delta_score = 0.0f;
    Group max_group = null;
    for (var group : this.groups) {
      if (group.accepts(TileType.Wheat) && group.addable()) {
        double dscore =
            group.calc_new_score(TileType.Wheat,
                                 group.coords_placable.iterator().next()) -
            group.calc_score();

        if (dscore >= max_delta_score) {
          max_delta_score = dscore;
          max_group = group;
        }
      }
    }

    if (max_group != null) {
      this.suggests_food.add(
          new PlacementSuggestion(TileType.Wheat, null, 0.0f, max_group));
      return;
    }

    // new wheat group
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    int max_windmill = 0;
    int max_beehive = 0;
    for (var cplacable : this.coords_placable) {
      boolean wheat_neighbors = false;

      int windmill = 0;
      int beehive = 0;
      for (var cring : cplacable.getRing(1)) {
        var t = world.getMap().at(cring);
        if (t == null)
          continue;
        if (t.getTileType() == TileType.Windmill)
          windmill++;
        else if (t.getTileType() == TileType.Beehive)
          beehive++;
        else if (t.getTileType() == TileType.Wheat) {
          wheat_neighbors = true;
          break;
        }
      }

      // refuse to start new wheat group beside wheats
      if (wheat_neighbors)
        continue;

      int avoid =
          this.avoid_coord_for_other_group(TileType.Wheat, cplacable, null);
      if (avoid <= low_avoid && windmill >= max_windmill &&
          beehive >= max_beehive) {
        low_avoid = avoid;
        max_windmill = windmill;
        max_beehive = beehive;
        best_cplacable = cplacable;
      }
    }
    if (best_cplacable != null) {
      try {
        this.suggests_food.add(
            new PlacementSuggestion(TileType.Wheat, best_cplacable, 0.0f,
                                    new WheatGroup(this, best_cplacable)));
      } catch (Exception e) {
      }
    }
  }

  void place_forest() {
    // beside the forest
    double max_delta_score = 0.0f;
    Group max_group = null;
    for (var group : this.groups) {
      if (group.accepts(TileType.Forest) && group.addable()) {
        double dscore =
            group.calc_new_score(TileType.Forest,
                                 group.coords_placable.iterator().next()) -
            group.calc_score();

        if (dscore >= max_delta_score) {
          max_delta_score = dscore;
          max_group = group;
        }
      }
    }

    if (max_group != null) {
      try {
        this.suggests_materials.add(new PlacementSuggestion(
            TileType.Forest, null, max_delta_score, max_group));
      } catch (Exception e) {
      }
      return;
    }

    int low_avoid = Integer.MAX_VALUE;
    CubeCoordinate best_cplacable = null;
    for (var cplacable : this.coords_placable) {
      int avoid =
          this.avoid_coord_for_other_group(TileType.Forest, cplacable, null);
      if (avoid <= low_avoid) {
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }
    if (best_cplacable != null) {
      try {
        ForestGroup new_group = new ForestGroup(this, best_cplacable);
        this.suggests_materials.add(new PlacementSuggestion(
            TileType.Forest, best_cplacable,
            new_group.calc_new_score(TileType.Forest, best_cplacable),
            new_group));

      } catch (Exception e) {
      }
    }
  }

  void place_doublehouse() {
    double max_delta_score = 0.0f;
    Group max_group = null;
    for (var group : this.groups) {
      if (group.accepts(TileType.DoubleHouse) && group.addable()) {
        double dscore =
            group.calc_new_score(TileType.DoubleHouse,
                                 group.coords_placable.iterator().next()) -
            group.calc_score();

        if (dscore >= max_delta_score) {
          max_delta_score = dscore;
          max_group = group;
        }
      }
    }

    if (max_group != null) {
      this.suggests_money.add(new PlacementSuggestion(
          TileType.DoubleHouse, null, max_delta_score, max_group));
      return;
    }

    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    int high_markets = 0;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int markets = 0;
      for (var cring : cplacable.getArea(3)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;
        if (t.getTileType() == TileType.Marketplace)
          markets++;
      }

      int avoid = this.avoid_coord_for_other_group(TileType.DoubleHouse,
                                                   cplacable, null);

      if (avoid <= low_avoid && markets >= high_markets) {
        best_cplacable = cplacable;
        low_avoid = avoid;
        high_markets = markets;
      }
    }

    if (best_cplacable != null) {
      try {
        HousingGroup hgroup =
            new HousingGroup(this, TileType.DoubleHouse, best_cplacable);
        this.suggests_materials.add(new PlacementSuggestion(
            TileType.DoubleHouse, best_cplacable,
            hgroup.calc_new_score(TileType.DoubleHouse, best_cplacable),
            hgroup));
      } catch (Exception e) {
      }
    }
  }

  void place_smallhouse() {
    Group max_group = null;
    double max_delta_score = 0.0f;
    for (var group : this.groups) {
      if (group.accepts(TileType.SmallHouse) && group.addable()) {
        double dscore =
            group.calc_new_score(TileType.SmallHouse,
                                 group.coords_placable.iterator().next()) -
            group.calc_score();

        if (dscore >= max_delta_score) {
          max_delta_score = dscore;
          max_group = group;
        }
      }
    }

    if (max_group != null) {
      this.suggests_materials.add(new PlacementSuggestion(
          TileType.SmallHouse, null, max_delta_score, max_group));
      return;
    }

    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    int low_count = 0;
    int high_markets = 0;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int count = 0;
      int markets = 0;
      for (var cring : cplacable.getRing(1)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;
        if (t.getTileType() == TileType.SmallHouse)
          count++;
        if (t.getTileType() == TileType.Marketplace)
          markets++;
      }

      int avoid = this.avoid_coord_for_other_group(TileType.SmallHouse,
                                                   cplacable, null);

      if (avoid <= low_avoid && low_count >= count && markets >= high_markets) {
        best_cplacable = cplacable;
        low_avoid = avoid;
        low_count = count;
        high_markets = markets;
      }
    }

    if (best_cplacable != null) {
      try {
        HousingGroup hgroup =
            new HousingGroup(this, TileType.SmallHouse, best_cplacable);
        this.suggests_materials.add(new PlacementSuggestion(
            TileType.SmallHouse, best_cplacable,
            hgroup.calc_new_score(TileType.SmallHouse, best_cplacable),
            hgroup));
      } catch (Exception e) {
      }
    }
  }

  void place_windmill() {
    // see docs
    int low_avoid = Integer.MAX_VALUE;
    double best_score = 0.0f;
    CubeCoordinate best_cplacable = null;

    for (var cplacable : this.coords_placable) {
      int count_wheat = 0;
      int w = 0;
      for (var cneighbor : cplacable.getArea(3)) {
        var ct = world.getMap().at(cneighbor);
        if (ct == null || ct.getTileType() != TileType.Wheat)
          continue;
        count_wheat++;

        int m = 0;
        for (var wheat_neighbor : cneighbor.getArea(3)) {
          var wct = world.getMap().at(wheat_neighbor);
          if (wct == null || wct.getTileType() != TileType.Windmill)
            continue;
          m++;
        }
        w += Math.min(1, 2.0f / m);
      }

      int f = 0;
      for (var cneighbor : cplacable.getRing(1)) {
        var ct = world.getMap().at(cneighbor);
        if (ct == null || ct.getTileType() != TileType.Forest)
          continue;
        f++;
        break;
      }
      if (f > 0)
        continue;

      int avoid =
          this.avoid_coord_for_other_group(TileType.Windmill, cplacable, null);

      double score = 2 * w * (1 - (f * 0.5f));

      if (score >= best_score && avoid <= low_avoid) {
        low_avoid = avoid;
        best_score = score;

        best_cplacable = cplacable;
      }
    }

    if (best_cplacable != null) {
      this.suggests_food.add(new PlacementSuggestion(
          TileType.Windmill, best_cplacable, best_score, null));
    }
  }

  void place_beehive() {
    // max wheet or forest in 2 radius
    CubeCoordinate best_cplacable = null;
    double best_score = 0.0f;
    int low_avoid = Integer.MAX_VALUE;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int f = 0;
      int w = 0;
      for (var cneighbor : cplacable.getArea(2)) {
        var ct = world.getMap().at(cneighbor);
        if (ct == null)
          continue;
        if (ct.getTileType() == TileType.Wheat)
          w++;
        if (ct.getTileType() == TileType.Forest)
          f++;
      }

      int avoid =
          this.avoid_coord_for_other_group(TileType.Beehive, cplacable, null);
      double score = Math.log((f + 1) * (w + 1)) * 0.6f;

      if (score >= best_score && avoid <= low_avoid) {
        low_avoid = avoid;
        best_score = score;
        best_cplacable = cplacable;
      }
    }

    if (best_cplacable != null) {
      this.suggests_food.add(new PlacementSuggestion(
          TileType.Beehive, best_cplacable, best_score, null));
    }
  }

  void place_stonequarry() {
    CubeCoordinate best_cplacable = null;
    int low_avoid = Integer.MAX_VALUE;
    double max_score = 0.0f;
    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int avoid = this.avoid_coord_for_other_group(TileType.StoneQuarry,
                                                   cplacable, null);

      int l = 0;
      for (var cring : cplacable.getRing(1)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;

        if (t.getTileType() != TileType.StoneHill &&
            t.getTileType() != TileType.StoneRocks &&
            t.getTileType() != TileType.StoneMountain)
          continue;

        int count_using_quarries = 0;
        for (var cquarry : cring.getRing(1)) {
          var tquarry = this.world.getMap().at(cquarry);
          if (tquarry == null)
            continue;
          if (tquarry.getTileType() == TileType.StoneQuarry)
            count_using_quarries++;
        }

        if (t.getTileType() == TileType.StoneMountain &&
            count_using_quarries <= 2)
          l = 3;
        else if (t.getTileType() == TileType.StoneHill &&
                 count_using_quarries <= 1)
          l = 2;
        else if (t.getTileType() == TileType.StoneRocks &&
                 count_using_quarries == 0)
          l = 1;
      }
      double score = 5 * l;

      if (score >= max_score && avoid <= low_avoid) {
        max_score = score;
        low_avoid = avoid;
        best_cplacable = cplacable;
      }
    }

    if (best_cplacable != null) {
      this.suggests_materials.add(new PlacementSuggestion(
          TileType.StoneQuarry, best_cplacable, max_score, null));
    }
  }

  void place_moai() {
    // see docs
    double max_score = 0.0f;
    int high_houses = 0;
    int low_avoid = Integer.MAX_VALUE;
    CubeCoordinate best_cplacable = null;

    for (var cplacable : this.coords_placable) {
      if (best_cplacable == null)
        best_cplacable = cplacable;

      int count_maoi_per_house = 0;
      for (var cring : cplacable.getRing(6)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;
        if (t == null || t.getTileType() != TileType.Moai)
          continue;

        count_maoi_per_house++;
      }

      Set<TileType> unique = new HashSet<>();
      for (var cneighbor : cplacable.getArea(4)) {
        var ct = world.getMap().at(cneighbor);
        if (ct == null)
          continue;
        unique.add(ct.getTileType());
      }

      int count = 0;
      for (var cring : cplacable.getRing(3)) {
        var t = this.world.getMap().at(cring);
        if (t == null)
          continue;
        if (t.getTileType() != TileType.SmallHouse ||
            t.getTileType() != TileType.DoubleHouse)
          continue;

        count++;
      }

      int avoid =
          this.avoid_coord_for_other_group(TileType.Moai, cplacable, null);

      double score = 1 + 0.5f * Math.max(0, unique.size() - 2);

      if (score >= max_score && avoid <= low_avoid) {
        low_avoid = avoid;
        max_score = score;

        best_cplacable = cplacable;
      }
    }

    if (best_cplacable != null) {
      this.suggests_money.add(new PlacementSuggestion(
          TileType.Moai, best_cplacable, max_score, null));
    }
  }

  boolean place_tile(TileType type, CubeCoordinate coord) {
    System.out.println("want " + type + " on " + coord);
    if (coord == null)
      return false;
    System.out.println("is_coord_usable: " +
                       (world.getMap().at(coord) == null));
    if (world.getMap().at(coord) != null)
      return false;

    if (!world.getBuildArea().contains(coord))
      return false;

    try {
      controller.placeTile(type, coord);
    } catch (Exception e) {
      System.out.println("SOMETHING VERY BAD HAPPENED HELP HELP: " +
                         e.toString());
    } finally {
      // world does not actually update when calling controller.placeTile() in
      // the same turn
      for (var cring : coord.getRing(1)) {
        if (world.getMap().at(cring) == null &&
            world.getBuildArea().contains(cring))
          this.coords_placable.add(cring);
      }

      if (this.coords_placable.contains(coord))
        this.coords_placable.remove(coord);

      for (var group : this.groups)
        group.update_coords_placable(coord);
    }

    // this.coords_placable.removeIf(t -> !world.getBuildArea().contains(t));
    return true;
  }

  int avoid_coord_for_other_group(TileType type, CubeCoordinate coord,
                                  Group current) {
    int should_avoid = 0;

    for (var group : this.groups) {
      if (group == current)
        continue;

      if (!group.coords_placable.contains(coord))
        continue;

      if (!group.fine_neighbor(type))
        should_avoid++;
    }

    return should_avoid;
  }

  void update_coords_placable() {
    for (var c : this.world.getBuildArea()) {
      if (world.getMap().at(c) != null)
        continue;
      if (world.getMap().getNeighbors(c).hasNext() == true)
        this.coords_placable.add(c);
    }

    for (var group : this.groups)
      group.update_coords_placable();
  }

  boolean market_prefers_food() {
    double perc_food = Math.min(Math.pow(Math.E, -(this.resource_growth.money /
                                                   this.resource_growth.food)),
                                1);
    double perc_mat =
        Math.min(Math.pow(Math.E, -(this.resource_growth.money /
                                    this.resource_growth.materials)),
                 1);
    return perc_food >= perc_mat;
  }

  double market_get_food_rate() {
    double perc_food =
        (this.resource_growth.money < this.resource_growth.food)
            ? Math.min(Math.pow(Math.E, -(this.resource_growth.money /
                                          this.resource_growth.food)),
                       1)
            : 0;
    return Math.clamp(perc_food, 0.0f, 1.0f);
  }

  double market_get_materials_rate() {
    double perc_mat =
        (this.resource_growth.money < this.resource_growth.materials)
            ? Math.min(Math.pow(Math.E, -(this.resource_growth.money /
                                          this.resource_growth.materials)),
                       1)
            : 0;
    return Math.clamp(perc_mat, 0.0f, 1.0f);
  }

  void setup_marketplaces() {
    for (var market : this.marketplaces.entrySet()) {
      if (market.getValue() == false) {
        // TODO(ayham-1): find a formula that reaches 1
        double perc_food = this.market_get_food_rate();
        double perc_mat = this.market_get_materials_rate();

        this.controller.configureMarket(market.getKey(), perc_food, perc_mat);
        this.marketplaces.put(market.getKey(), true);
        if (!this.controller.actionPossible())
          return;
      }
    }
  }

  void group_count_set(TileType type, CubeCoordinate start,
                       Set<CubeCoordinate> coords, World world) {

    if (world.getMap().at(start).getTileType() == type) {
      coords.add(world.getMap().at(start).getCoordinate());
    }

    for (var cneighbor : world.getMap().at(start).getCoordinate().getRing(1)) {
      var tneighbor = world.getMap().at(cneighbor);
      if (tneighbor == null)
        continue;

      if (tneighbor.getTileType() == type) {
        if (!coords.contains(tneighbor.getCoordinate())) {
          coords.add(tneighbor.getCoordinate());
          group_count_set(type, cneighbor, coords, world);
        }
      }
    }
  }

  int group_count(TileType type, CubeCoordinate start, World world) {
    Set<CubeCoordinate> coord = new LinkedHashSet<CubeCoordinate>();
    group_count_set(type, start, coord, world);

    return coord.size();
  }

  void update_resources_stat() {
    this.resource_current = world.getResources();
    this.resource_target = world.getTargetResources();
    this.resource_growth = world.getResourcesRate();
    this.round_time_left = world.getRoundTime();

    this.reachable_money =
        (this.resource_current.money +
         (this.resource_growth.money * this.round_time_left)) >=
        this.resource_target.money;

    this.reachable_food =
        (this.resource_current.food +
         (this.resource_growth.food * this.round_time_left)) >=
        this.resource_target.food;

    this.reachable_materials =
        (this.resource_current.materials +
         (this.resource_growth.materials * this.round_time_left)) >=
        this.resource_target.materials;

    this.resource_perc_money =
        this.resource_current.money / this.resource_target.money;
    this.resource_perc_food =
        this.resource_current.food / this.resource_target.food;
    this.resource_perc_materials =
        this.resource_current.materials / this.resource_target.materials;

    if (this.world.getHand().isEmpty() && event_empty_hand_start) {

      this.resource_growth_last = this.resource_growth;
    }

    if (PRINT_DEBUG) {
      System.out.println("[current] money=" + this.resource_current.money +
                         " food=" + this.resource_current.food +
                         " materials=" + this.resource_current.materials);

      System.out.println("[target] money=" + this.resource_target.money +
                         " food=" + this.resource_target.food +
                         " materials=" + this.resource_target.materials);

      System.out.println("[growth] money=" + this.resource_growth.money +
                         " food=" + this.resource_growth.food +
                         " materials=" + this.resource_growth.materials);

      System.out.println(
          "[delta] money=" + this.resource_growth_delta.money +
          " food=" + this.resource_growth_delta.food +
          " materials=" + this.resource_growth_delta.materials);

      System.out.println(
          "[perc] money=" + String.format("%.3f", this.resource_perc_money) +
          " food=" + String.format("%.3f", this.resource_perc_food) +
          " materials=" +
          String.format("%.3f", this.resource_perc_materials));
    }
  }
}
