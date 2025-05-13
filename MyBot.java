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

/*
 * Observations:
 *  - only one placement of a tile is allowed per turn
 *  - memory is more abundant than time!
 *  - the longer a tile is placed the better,
 *  - more tiles placed earlier == better
 *  - marketplaces convert food/material to money, and are actually very
 * important mid-game
 *  - after placing, the getMap() does not update
 *  - avoid() functionality with if ( && ) is not exactly most efficient
 *  - wheat groups can be larger than 9 if windmills are involved [geogebra]
 *  - dynamic rules are probably good
 *  - !!!begin new wheat groups in expectation of windmills!!!
 *
 *  Score Calculation Optimization Ideas:
 *  - groups use their tiles when not suggesting new groups
 *  - remember tiles also per Group/TileRepr
 *
 * Proposed Rules:
 *  - [x] force windmills to be secluded when placing them without any wheat
 * neighbors
 *  - [x] cap wheat group growth so that more windmills have more wheats
 *  - [-] allow space beside wheat groups for windmills
 *  - [ ] force windmills so that they stack but not in range of wheats
 *
 * Current:
 *
 * TODOs:
 *
 * PROBLEMATIC SEEDS:
 * Bob II:
 *  - 4544524656413520667
 *  - 15232417153697053033
 *  - 6476645629748188223
 *  - 17568238662914506546
 *  - 4905031204093094641
 *  - 13159963882866694334
 *  - 6196504161327325917
 *  - 4651505693100974723
 *
 *  - WTF IS HAPPENING HEREEEEE
 *  - 11201007578510289452
 *  - 18278146962012266910
 *
 *
 * Bob I:
 *  - 206291997783329192
 *  - 11841930331551995945
 *  - 15983273597267596929
 *  - 3670905267582609409
 *  - 3833241609646726404
 *  - 13643824703136673852
 *  - 4185536861074494110
 *  - 9404526250308863753
 *  - 7166311343533290299
 *  - 10657404372395100657
 *  - 1524678160230742459
 *
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

public class MyBot extends ChallengeBot {
  static final boolean PRINT_DEBUG = true;

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
    return "Bob the Bot the Benevolent Builder II";
  }

  public enum ResourceType { Food, Materials, Money }

  class PlacementSuggestion implements Comparable<PlacementSuggestion> {
    Score growth_delta;
    CubeCoordinate coord = null;
    TileInfo info;

    PlacementSuggestion(TileType typ, CubeCoordinate coord, Score g_delta,
                        TileInfo info) {
      this.coord = coord;
      this.growth_delta = g_delta;
      this.info = info;
    }

    @Override
    public int compareTo(PlacementSuggestion other) {
      return this.growth_delta.compareTo(other.growth_delta);
    }

    public String toString() { return "suggested=" + this.info.type; }
  };

  class TileInfo {
    TileType type;
    Group associated_group;
    TileRepr associated_repr;

    TileInfo(TileType t, Group grp) {
      this.type = t;
      this.associated_group = grp;
    }

    TileInfo(TileType t, TileRepr repr) {
      this.type = t;
      this.associated_repr = repr;
    }
  }

  class Score implements Comparable<Score> {
    double food;
    double materials;
    double money;

    Score(double food, double materials, double money) {
      this.food = food;
      this.materials = materials;
      this.money = money;
    }

    Score delta(Score other) {
      return new Score(this.food - other.food, this.materials - other.materials,
                       this.money - other.money);
    }

    void add(Score other) {
      this.food += other.food;
      this.materials += other.materials;
      this.money += other.money;
    }

    @Override
    public int compareTo(Score other) {
      return Double.compare((this.food + this.materials + this.money),
                            (other.food + other.materials + other.money));
    }

    public int weighted_compareTo(Score other, ResourceType restype,
                                  double weight) {
      double here = 0.0f;
      here += (this.food) * ((restype == ResourceType.Food) ? weight : 1.0f);
      here += (this.materials) *
              ((restype == ResourceType.Materials) ? weight : 1.0f);
      here += (this.money) * ((restype == ResourceType.Money) ? weight : 1.0f);

      double there = 0.0f;
      there += (other.food) * ((restype == ResourceType.Food) ? weight : 1.0f);
      there += (other.materials) *
               ((restype == ResourceType.Materials) ? weight : 1.0f);
      there +=
          (other.money) * ((restype == ResourceType.Money) ? weight : 1.0f);

      return Double.compare(here, there);
    }

    public String toString() {
      return "food=" + this.food + " materials=" + this.materials +
          " money=" + this.money;
    }
  }

  abstract class Group {
    MyBot bot = null;

    HashMap<CubeCoordinate, TileType> tiles = new HashMap<>();
    Set<CubeCoordinate> coords_placable = new LinkedHashSet<>();
    Set<CubeCoordinate> coords_placable_potential = new LinkedHashSet<>();

    Group(MyBot bot) { this.bot = bot; }

    ResourceType res_type;
    TileType type;

    abstract Score calc_score();
    abstract Score calc_member_score(CubeCoordinate coord);

    abstract boolean accepts(TileType type);
    abstract boolean affects(TileType addition);

    boolean addable() { return !this.coords_placable.isEmpty(); }

    boolean is_on_border() {
      for (var coord : this.tiles.keySet()) {
        for (var cring : coord.getRing(1)) {
          if (!this.bot.world.getBuildArea().contains(cring))
            return true;
        }
      }
      return false;
    }

    public Score calc_suggested_new_relevant_score(TileInfo info,
                                                   CubeCoordinate coord) {
      this.bot.map.put(coord, info);
      this.tiles.put(coord, info.type);
      Score score = this.bot.director.get_relevant_score(info, coord);
      this.tiles.remove(coord);
      this.bot.map.remove(coord);
      return score;
    }

    public void suggest_group() {
      for (var cplacable : this.coords_placable) {
        TileInfo info = new TileInfo(this.type, this);
        Score orig = this.bot.director.get_relevant_score(info, cplacable);
        Score s = this.calc_suggested_new_relevant_score(info, cplacable);
        this.bot.director.suggest(
            new PlacementSuggestion(this.type, cplacable, s.delta(orig), info));
      }
    }

    public void suggest_all() {
      for (var cplacable : this.bot.coords_placable) {
        if (this.bot.state.managed_placable_coords.contains(cplacable))
          continue;

        TileInfo info = new TileInfo(this.type, this);
        Score s = this.calc_suggested_new_relevant_score(info, cplacable);
        s.add(this.calc_score());
        this.bot.director.suggest(new PlacementSuggestion(
            this.type, cplacable,
            s.delta(this.bot.director.get_relevant_score(info, cplacable)),
            info));
      }
    }

    public void add_tile(TileType type, CubeCoordinate coord) {
      this.tiles.put(coord, type);

      this.coords_placable.remove(coord);
      this.coords_placable_potential.remove(coord);
      this.bot.state.managed_placable_coords.remove(coord);

      for (var cring : coord.getRing(1)) {
        if (this.bot.map.get(cring) == null &&
            world.getBuildArea().contains(cring)) {
          this.coords_placable.add(cring);
          this.bot.state.managed_placable_coords.add(cring);
        }
        if (this.bot.map.get(cring) == null ||
            !world.getBuildArea().contains(cring))
          this.coords_placable_potential.add(cring);
      }
    }

    public void update_coords_placable() { this.update_coords_placable(null); }

    public void update_coords_placable(CubeCoordinate coord) {
      this.coords_placable = new LinkedHashSet<>();
      this.coords_placable_potential = new LinkedHashSet<>();
      for (var c : tiles.keySet()) {
        for (var cring : c.getRing(1)) {
          if (!cring.equals(coord) &&
              this.bot.world.getMap().at(cring) == null &&
              this.bot.world.getBuildArea().contains(cring)) {
            this.coords_placable.add(cring);
          }
          if (!cring.equals(coord) &&
              this.bot.world.getMap().at(cring) == null) {
            this.coords_placable_potential.add(cring);
          }
        }
      }
    }
  }

  class WheatGroup extends Group {
    /* Represents a Wheat group, optimal means:
     * - have 9 connected Wheat Tiles
     **/

    WheatGroup(MyBot bot) {
      super(bot);
      super.res_type = ResourceType.Food;
      super.type = TileType.Wheat;
    }

    public double calc_score_of_wheats(CubeCoordinate coord) {
      double w = Math.max((double)this.bot.group_count(TileType.Wheat, coord),
                          this.tiles.size());
      double s = (w <= 9) ? 4.0f : -2.5f;
      double individual =
          (Math.pow(Math.E, -Math.pow(((w - 9.0f) / s), 2.0f)) * 2.4f) + 0.1f;
      return w * individual;
    }

    @Override
    public Score calc_member_score(CubeCoordinate coord) {
      double w = Math.max((double)this.bot.group_count(TileType.Wheat, coord),
                          this.tiles.size());
      double s = (w <= 9) ? 4.0f : -2.5f;
      double ind =
          (Math.pow(Math.E, -Math.pow(((w - 9.0f) / s), 2.0f)) * 2.4f) + 0.1f;
      return new Score(ind, 0.0f, 0.0f);
    }

    @Override
    public Score calc_score() {
      double s = 0.0f;
      if (this.tiles.keySet().iterator().hasNext())
        s = this.calc_score_of_wheats(this.tiles.keySet().iterator().next());
      return new Score(s, 0.0f, 0.0f);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Wheat;
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Marketplace || type == TileType.Moai ||
          type == TileType.Beehive || type == TileType.Windmill ||
          type == TileType.Wheat;
    }
  }

  class ForestGroup extends Group {
    /* Represents a forest group, optimal means:
     * - most neighboring forest tiles
     * - group members all close together (implied)
     **/

    ForestGroup(MyBot bot) {
      super(bot);
      super.res_type = ResourceType.Materials;
      super.type = TileType.Forest;
    }

    public double calc_score_of_forest(CubeCoordinate start) {
      Set<CubeCoordinate> coords = new LinkedHashSet<CubeCoordinate>();
      this.bot.group_count_set(TileType.Forest, start, coords);
      double score = 0;
      for (var c : coords) {
        int count = 0;
        for (var cring : c.getRing(1)) {
          var tileinfo = this.bot.map.get(cring);
          if (tileinfo != null && tileinfo.type == TileType.Forest)
            count++;
        }
        score += count * 0.5f + 0.25f;
      }
      return score;
    }

    @Override
    public Score calc_member_score(CubeCoordinate coord) {
      int count = 0;
      for (var cring : coord.getRing(1)) {
        var tileinfo = this.bot.map.get(cring);
        if (tileinfo != null && tileinfo.type == TileType.Forest)
          count++;
      }
      double d = count * 0.5f + 0.25f;
      return new Score(d, 0.0f, 0.0f);
    }

    @Override
    public Score calc_score() {
      double s = 0.0f;
      if (this.tiles.keySet().iterator().hasNext())
        s = this.calc_score_of_forest(this.tiles.keySet().iterator().next());
      return new Score(0.0f, s, 0.0f);
    }

    @Override
    public boolean accepts(TileType type) {
      return type == TileType.Forest;
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Forest || type == TileType.Marketplace ||
          type == TileType.Moai || type == TileType.Beehive;
    }
  }

  abstract class TileRepr {
    MyBot bot;
    CubeCoordinate coord;
    TileType type;

    TileRepr(MyBot b, TileType t) {
      this.bot = b;
      this.type = t;
    }

    // TileRepr has been accepted, so now remember position
    public void set_coord(CubeCoordinate c) { this.coord = c; }

    public Score calc_suggested_new_relevant_score(TileInfo info,
                                                   CubeCoordinate coord) {
      this.bot.map.put(coord, info);
      Score score = this.bot.director.get_relevant_score(info, coord);
      this.bot.map.remove(coord);
      return score;
    }

    abstract Score calc_score(CubeCoordinate coord);
    Score calc_score() { return this.calc_score(this.coord); }

    abstract boolean affects(TileType type);

    public void suggest_all() {
      for (var cplacable : this.bot.coords_placable) {
        TileInfo info = new TileInfo(this.type, this);
        Score s = this.calc_suggested_new_relevant_score(info, cplacable);
        s.add(this.calc_score(cplacable));

        this.bot.director.suggest(new PlacementSuggestion(
            type, cplacable,
            s.delta(this.bot.director.get_relevant_score(info, cplacable)),
            info));
      }
    }

    public boolean in_range(CubeCoordinate coord, int distance) {
      if (this.coord.distance(coord) > distance)
        return false;
      return true;
    }

    abstract boolean in_range(CubeCoordinate coord, TileType type);
  }

  class WindmillRepr extends TileRepr {
    WindmillRepr(MyBot bot) { super(bot, TileType.Windmill); }

    public boolean has_space() {
      for (var c : this.coord.getArea(3)) {
        if (this.bot.map.get(c) == null)
          // if (this.bot.world.getMap().at(c) == null)
          return true;
      }
      return false;
    }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      double w = 0;
      for (var cneighbor : coord.getArea(3)) {
        var ct = this.bot.map.get(cneighbor);
        if (ct == null || ct.type != TileType.Wheat)
          continue;

        int m = 0;
        for (var wheat_neighbor : cneighbor.getArea(3)) {
          var wct = this.bot.map.get(wheat_neighbor);
          if (wct == null || wct.type != TileType.Windmill)
            continue;
          m++;
        }

        w += Math.min(1.0f, 2.0f / m);
      }

      int f = 0;
      for (var cneighbor : coord.getRing(1)) {
        var ct = this.bot.map.get(cneighbor);
        if (ct == null || ct.type != TileType.Forest)
          continue;
        f++;
      }

      double score = 2.0f * w * (1.0f - (f * 0.5f));

      return new Score(score, 0.0f, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Marketplace || type == TileType.Moai ||
          type == TileType.Windmill;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      if (type == TileType.Wheat)
        return this.in_range(coord, 3);
      else if (type == TileType.Forest)
        return this.in_range(coord, 1);
      else
        return this.in_range(coord, 0);
    }
  }

  class BeehiveRepr extends TileRepr {
    BeehiveRepr(MyBot bot) { super(bot, TileType.Beehive); }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      int f = 0;
      int w = 0;
      for (var cneighbor : coord.getArea(2)) {
        var ct = this.bot.map.get(cneighbor);
        if (ct == null)
          continue;
        if (ct.type == TileType.Wheat)
          w++;
        if (ct.type == TileType.Forest)
          f++;
      }

      double score = Math.log((f + 1.0f) * (w + 1.0f)) * 0.6f;
      return new Score(score, 0.0f, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Marketplace || type == TileType.Moai;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 2);
    }
  }

  class MoaiRepr extends TileRepr {
    CubeCoordinate coord;

    MoaiRepr(MyBot bot) { super(bot, TileType.Moai); }

    public double calc_boost(CubeCoordinate coord) {
      Set<TileType> unique = new LinkedHashSet<>();
      for (var cneighbor : coord.getArea(4)) {
        var un = this.bot.map.get(cneighbor);
        if (un != null)
          unique.add(un.type);
      }
      double score = 1.0f + 0.5f * Math.max(0, unique.size() - 2.0f);
      return score;
    }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      return new Score(0.0f, 0.0f, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.SmallHouse || type == TileType.DoubleHouse;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 4);
    }
  }

  class MarketplaceRepr extends TileRepr {
    boolean configured = false;
    double perc_food = 0.0f;
    double perc_mat = 0.0f;

    MarketplaceRepr(MyBot bot) { super(bot, TileType.Marketplace); }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      double f_rate = this.bot.market_get_food_rate();
      double m_rate = this.bot.market_get_materials_rate();

      int h = 0;
      int d = 0;
      for (var cring : coord.getRing(1)) {
        var t = this.bot.map.get(cring);
        if (t == null)
          continue;

        if (t.type == TileType.SmallHouse)
          h++;
        if (t.type == TileType.DoubleHouse)
          d++;
      }
      double e = Math.min(1, ((h + (d * 2.0f)) * 0.4f + 0.2f));

      double f = 0.0f;
      double m = 0.0f;

      for (var cneighbor : coord.getArea(3)) {
        var ct = this.bot.map.get(cneighbor);
        if (ct == null)
          continue;

        if (this.bot.get_resource_type(ct.type) == ResourceType.Food) {
          var tileinfo = this.bot.map.get(cneighbor);
          if (tileinfo.associated_group != null)
            f += tileinfo.associated_group.calc_member_score(cneighbor).food;
          if (tileinfo.associated_repr != null)
            f += tileinfo.associated_repr.calc_score(cneighbor).food;
        } else if (this.bot.get_resource_type(ct.type) ==
                   ResourceType.Materials) {
          var tileinfo = this.bot.map.get(cneighbor);
          if (tileinfo.associated_group != null)
            m += tileinfo.associated_group.calc_member_score(cneighbor)
                     .materials;
          if (tileinfo.associated_repr != null)
            m += tileinfo.associated_repr.calc_score(cneighbor).materials;
        }
      }

      double score = (f * f_rate + m * m_rate) * e;

      return new Score(0.0f, 0.0f, score);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.SmallHouse ||
          type == TileType.DoubleHouse;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 1);
    }
  }

  class DoubleHouseRepr extends TileRepr {
    DoubleHouseRepr(MyBot bot) { super(bot, TileType.DoubleHouse); }

    public double calc_score_of_dhouse(CubeCoordinate coord) {
      double best_boost = this.bot.get_best_boost(coord);
      int count = 0;
      for (var cring : coord.getRing(1)) {
        var tileinfo = this.bot.map.get(cring);
        if (tileinfo == null)
          continue;

        if (tileinfo.type == TileType.DoubleHouse ||
            tileinfo.type == TileType.SmallHouse)
          count++;
      }
      double score =
          (((-Math.abs((double)count - 3.0f) + 3.0f) / (2.0f / 3.0f)));

      return Math.max(score * best_boost, Math.max(score, 2.0f));
    }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      return new Score(0.0f, 0.0f, this.calc_score_of_dhouse(coord));
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.Marketplace ||
          type == TileType.DoubleHouse;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 1);
    }
  }

  class SmallHouseRepr extends TileRepr {
    SmallHouseRepr(MyBot bot) { super(bot, TileType.SmallHouse); }

    public double calc_score_of_shouse(CubeCoordinate coord) {
      double best_boost = this.bot.get_best_boost(coord);
      return Math.max((2.0f * best_boost), 2.0f);
    }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      return new Score(0.0f, 0.0f, this.calc_score_of_shouse(coord));
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.Marketplace ||
          type == TileType.DoubleHouse;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 0);
    }
  }

  class GrassRepr extends TileRepr {
    GrassRepr(MyBot bot) { super(bot, TileType.Grass); }

    // lol by itself it does nothing, but added to the map, maybe
    @Override
    public Score calc_score(CubeCoordinate coord) {
      return new Score(0.0f, 0.0f, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 0);
    }
  }

  class StoneRepr extends TileRepr {
    StoneRepr(MyBot bot, TileType stone) { super(bot, stone); }

    // lol by itself it does nothing, but added to the map, maybe
    @Override
    public Score calc_score(CubeCoordinate coord) {
      return new Score(0.0f, 0.0f, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.StoneQuarry;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      return this.in_range(coord, 0);
    }
  }

  class QuarryRepr extends TileRepr {
    QuarryRepr(MyBot bot) { super(bot, TileType.StoneQuarry); }

    @Override
    public Score calc_score(CubeCoordinate coord) {
      int max_l = 0;
      for (var cring : coord.getRing(1)) {
        var t = this.bot.map.get(cring);
        if (t == null)
          continue;

        if (t.type != TileType.StoneHill && t.type != TileType.StoneRocks &&
            t.type != TileType.StoneMountain)
          continue;

        int count_using_quarries = 0;
        for (var cquarry : cring.getRing(1)) {
          var tquarry = this.bot.map.get(cquarry);
          if (tquarry == null)
            continue;
          if (tquarry.type == TileType.StoneQuarry)
            count_using_quarries++;
        }

        int l = 0;
        if (t.type == TileType.StoneMountain && count_using_quarries < 3)
          l = 3;
        else if (t.type == TileType.StoneHill && count_using_quarries < 2)
          l = 2;
        else if (t.type == TileType.StoneRocks && count_using_quarries < 1)
          l = 1;

        if (l >= max_l)
          max_l = l;
      }

      double score = 5.0f * max_l;
      return new Score(0.0f, score, 0.0f);
    }

    @Override
    public boolean affects(TileType type) {
      return type == TileType.Moai || type == TileType.StoneQuarry;
    }

    @Override
    boolean in_range(CubeCoordinate coord, TileType type) {
      if (type == TileType.StoneMountain || type == TileType.StoneRocks ||
          type == TileType.StoneHill)
        return this.in_range(coord, 2);
      else
        return this.in_range(coord, 0);
    }
  }

  class State {
    // holds the various lists for the bot that are used to represent
    // information about the game map in a non Map<>'y fashion

    // the haves
    Set<Group> wheats = new LinkedHashSet<>();
    Set<Group> forests = new LinkedHashSet<>();
    Set<TileRepr> windmills = new LinkedHashSet<>();
    Set<TileRepr> quarries = new LinkedHashSet<>();
    Set<TileRepr> marketplaces = new LinkedHashSet<>();
    Set<TileRepr> dhouses = new LinkedHashSet<>();
    Set<TileRepr> shouses = new LinkedHashSet<>();
    Set<TileRepr> beehives = new LinkedHashSet<>();

    // the have-nots
    Set<TileRepr> moais = new LinkedHashSet<>();
    Set<TileRepr> grass = new LinkedHashSet<>();
    Set<TileRepr> rocks = new LinkedHashSet<>();
    Set<TileRepr> hills = new LinkedHashSet<>();
    Set<TileRepr> mountains = new LinkedHashSet<>();

    // managed placable_coords
    Set<CubeCoordinate> managed_placable_coords = new LinkedHashSet<>();
  }

  State state = new State();

  abstract class Director {
    MyBot bot;
    RuleSet rules;
    int redraw_counter = 0;

    TileType chosen_tile;
    ArrayList<PlacementSuggestion> suggestions = new ArrayList<>();

    abstract boolean do_redraw();
    abstract int redraw_max_times();
    abstract PlacementSuggestion pick();

    Director(MyBot bot) {
      this.bot = bot;
      this.rules = new RuleSet(this.bot);
    }

    void new_card() { this.suggestions.clear(); }

    void suggest(PlacementSuggestion suggestion) {
      this.suggestions.add(suggestion);
    }

    int redraw_max_times(double grad, double mult, double offset) {
      return (int)Math.floor(
          Math.pow(Math.E, -((grad * this.bot.round * mult) + offset)));
    }

    boolean do_redraw(double grad, double e_off) {
      var cost = this.bot.world.getRedrawCosts();
      boolean redrawable =
          this.bot.resource_current.money >= cost.money &&
          this.bot.resource_current.food >= cost.food &&
          this.bot.resource_current.materials >= cost.materials;

      int times_allowed = this.redraw_max_times();
      if (redrawable && times_allowed >= this.redraw_counter &&
          this.bot.world.getHand().isEmpty()) {
        double money =
            this.bot.resource_current.money +
            this.bot.resource_growth.money * this.bot.round_time_left;
        double food = this.bot.resource_current.food +
                      this.bot.resource_growth.food * this.bot.round_time_left;
        double mat =
            this.bot.resource_current.materials +
            this.bot.resource_growth.materials * this.bot.round_time_left;

        double offset_m = (this.bot.resource_growth_delta.money /
                           this.bot.resource_delta_count) *
                          this.bot.round_time_left;
        double offset_f = (this.bot.resource_growth_delta.food /
                           this.bot.resource_delta_count) *
                          this.bot.round_time_left;
        double offset_mat = (this.bot.resource_growth_delta.materials /
                             this.bot.resource_delta_count) *
                            this.bot.round_time_left;

        double early_bias =
            1 - (Math.pow(Math.E,
                          -(grad * (double)this.bot.round +
                            (this.bot.world.getRoundTime() / 60.0f) + e_off)));

        if ((money - cost.money) + offset_m >
                this.bot.resource_target.money * early_bias &&
            (food - cost.food) + offset_f >
                this.bot.resource_target.food * early_bias &&
            (mat - cost.materials) + offset_mat >
                this.bot.resource_target.materials * early_bias) {
          this.redraw_counter++;
          return true;
        }
      }
      return false;
    }

    void ask_for_suggestions(TileType card) {
      Set<Group> group_set = null;

      // do creating new groups
      if (card == TileType.Marketplace) {
        MarketplaceRepr repr = new MarketplaceRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.Grass) {
        GrassRepr repr = new GrassRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.StoneHill) {
        StoneRepr repr = new StoneRepr(this.bot, card);
        repr.suggest_all();
      } else if (card == TileType.StoneMountain) {
        StoneRepr repr = new StoneRepr(this.bot, card);
        repr.suggest_all();
      } else if (card == TileType.StoneRocks) {
        StoneRepr repr = new StoneRepr(this.bot, card);
        repr.suggest_all();
      } else if (card == TileType.Wheat) {
        group_set = this.bot.state.wheats;
        WheatGroup grp = new WheatGroup(this.bot);
        grp.suggest_all();
      } else if (card == TileType.Forest) {
        group_set = this.bot.state.forests;
        ForestGroup grp = new ForestGroup(this.bot);
        grp.suggest_all();
      } else if (card == TileType.DoubleHouse) {
        DoubleHouseRepr repr = new DoubleHouseRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.SmallHouse) {
        SmallHouseRepr repr = new SmallHouseRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.Windmill) {
        WindmillRepr repr = new WindmillRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.Beehive) {
        BeehiveRepr repr = new BeehiveRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.StoneQuarry) {
        QuarryRepr repr = new QuarryRepr(this.bot);
        repr.suggest_all();
      } else if (card == TileType.Moai) {
        MoaiRepr repr = new MoaiRepr(this.bot);
        repr.suggest_all();
      }

      if (group_set != null) {
        for (var group : group_set)
          if (group.addable())
            group.suggest_group();
      }
    }

    void accept_suggestion(PlacementSuggestion suggestion) {
      if (PRINT_DEBUG)
        System.out.println("[sdelta] " + suggestion.growth_delta.toString());

      // i'm sorry for this atrocity...
      if (suggestion.info.associated_group != null) {
        if (suggestion.info.associated_group instanceof WheatGroup)
          this.bot.state.wheats.add(suggestion.info.associated_group);
        else if (suggestion.info.associated_group instanceof ForestGroup)
          this.bot.state.forests.add(suggestion.info.associated_group);
      } else if (suggestion.info.associated_repr != null) {
        if (suggestion.info.associated_repr instanceof WindmillRepr)
          this.bot.state.windmills.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof QuarryRepr)
          this.bot.state.quarries.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof MarketplaceRepr)
          this.bot.state.marketplaces.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof MoaiRepr)
          this.bot.state.moais.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof GrassRepr)
          this.bot.state.grass.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof DoubleHouseRepr)
          this.bot.state.dhouses.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof SmallHouseRepr)
          this.bot.state.shouses.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof BeehiveRepr)
          this.bot.state.beehives.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof StoneRepr &&
                 suggestion.info.type == TileType.StoneRocks)
          this.bot.state.rocks.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof StoneRepr &&
                 suggestion.info.type == TileType.StoneHill)
          this.bot.state.hills.add(suggestion.info.associated_repr);
        else if (suggestion.info.associated_repr instanceof StoneRepr &&
                 suggestion.info.type == TileType.StoneMountain)
          this.bot.state.mountains.add(suggestion.info.associated_repr);
      }

      if (suggestion.info.associated_group != null)
        suggestion.info.associated_group.add_tile(suggestion.info.type,
                                                  suggestion.coord);

      if (suggestion.info.associated_repr != null)
        suggestion.info.associated_repr.set_coord(suggestion.coord);

      this.bot.place_tile(suggestion.coord, suggestion.info);
    }

    Score get_global_score() {
      Score score = new Score(0.0f, 0.0f, 0.0f);

      for (var wheat_grp : this.bot.state.wheats)
        score.add(wheat_grp.calc_score());

      for (var forest_grp : this.bot.state.forests)
        score.add(forest_grp.calc_score());

      for (var windmill : this.bot.state.windmills)
        score.add(windmill.calc_score());

      for (var quarry : this.bot.state.quarries)
        score.add(quarry.calc_score());

      for (var marketplace : this.bot.state.marketplaces)
        score.add(marketplace.calc_score());

      return score;
    };

    Score get_relevant_score(TileInfo info, CubeCoordinate coord) {
      Score score = new Score(0.0f, 0.0f, 0.0f);

      if (info.associated_repr != null) {
        if (info.associated_repr.affects(TileType.Wheat))
          for (var wheat_grp : this.bot.state.wheats)
            score.add(wheat_grp.calc_score());

        if (info.associated_repr.affects(TileType.Forest))
          for (var forest_grp : this.bot.state.forests)
            score.add(forest_grp.calc_score());

        if (info.associated_repr.affects(TileType.Windmill)) {
          for (var windmill : this.bot.state.windmills)
            if (windmill.in_range(coord, info.type)) {
              Score s = windmill.calc_score();
              score.add(s);
            }
        }

        if (info.associated_repr.affects(TileType.StoneQuarry))
          for (var quarry : this.bot.state.quarries)
            if (quarry.in_range(coord, info.type))
              score.add(quarry.calc_score());

        if (info.associated_repr.affects(TileType.Marketplace))
          for (var marketplace : this.bot.state.marketplaces)
            if (marketplace.in_range(coord, info.type))
              score.add(marketplace.calc_score());

        if (info.associated_repr.affects(TileType.Beehive))
          for (var beehive : this.bot.state.beehives)
            if (beehive.in_range(coord, info.type))
              score.add(beehive.calc_score());

        if (info.associated_repr.affects(TileType.Moai))
          for (var moai : this.bot.state.moais)
            if (moai.in_range(coord, info.type))
              score.add(moai.calc_score());

        if (info.associated_repr.affects(TileType.DoubleHouse))
          for (var dhouse : this.bot.state.dhouses)
            if (dhouse.in_range(coord, info.type))
              score.add(dhouse.calc_score());

        if (info.associated_repr.affects(TileType.SmallHouse))
          for (var shouse : this.bot.state.shouses)
            if (shouse.in_range(coord, info.type))
              score.add(shouse.calc_score());

      } else if (info.associated_group != null) {
        if (info.associated_group.affects(TileType.Wheat))
          for (var wheat_grp : this.bot.state.wheats)
            score.add(wheat_grp.calc_score());

        if (info.associated_group.affects(TileType.Forest))
          for (var forest_grp : this.bot.state.forests)
            score.add(forest_grp.calc_score());

        if (info.associated_group.affects(TileType.Windmill)) {
          for (var windmill : this.bot.state.windmills)
            if (windmill.in_range(coord, info.type)) {
              score.add(windmill.calc_score());
              Score s = windmill.calc_score();
              score.add(s);
            }
        }

        if (info.associated_group.affects(TileType.StoneQuarry))
          for (var quarry : this.bot.state.quarries)
            if (quarry.in_range(coord, info.type))
              score.add(quarry.calc_score());

        if (info.associated_group.affects(TileType.Marketplace))
          for (var marketplace : this.bot.state.marketplaces)
            if (marketplace.in_range(coord, info.type))
              score.add(marketplace.calc_score());

        if (info.associated_group.affects(TileType.Beehive)) {
          for (var beehive : this.bot.state.beehives)
            if (beehive.in_range(coord, info.type))
              score.add(beehive.calc_score());
        }

        if (info.associated_group.affects(TileType.Moai))
          for (var moai : this.bot.state.moais)
            if (moai.in_range(coord, info.type))
              score.add(moai.calc_score());
      }

      return score;
    };

    TileType get_preferred_tile(List<TileType> hand) {
      ResourceType needed = ResourceType.Money;
      if (this.bot.resource_growth.food <= this.bot.resource_growth.money &&
          this.bot.resource_growth.food <= this.bot.resource_growth.materials) {
        needed = ResourceType.Food;
      } else if (this.bot.resource_growth.money <=
                     this.bot.resource_growth.food &&
                 this.bot.resource_growth.money <=
                     this.bot.resource_growth.materials) {
        needed = ResourceType.Money;
      } else if (this.bot.resource_growth.materials <=
                     this.bot.resource_growth.money &&
                 this.bot.resource_growth.materials <=
                     this.bot.resource_growth.food) {
        needed = ResourceType.Materials;
      }

      ArrayList<TileType> cards = new ArrayList<>();

      for (var card : hand) {
        if (this.bot.get_resource_type(card) == needed)
          cards.add(card);
      }

      if (needed == ResourceType.Food) {
        for (var card : cards) {
          if (card == TileType.Windmill)
            return card;
          if (card == TileType.Wheat)
            return card;
        }
      } else if (needed == ResourceType.Money) {
        for (var card : cards) {
          if (card == TileType.DoubleHouse)
            return card;
          if (card == TileType.Marketplace)
            return card;
        }
      } else if (needed == ResourceType.Materials) {
        for (var card : cards) {
          if (card == TileType.StoneQuarry)
            return card;
        }
      }

      return (cards.isEmpty()) ? hand.iterator().next() : cards.getFirst();
    }

    class RuleSet {
      MyBot bot;
      RuleSet(MyBot b) { this.bot = b; }

      boolean apply_wheat_groups_size(PlacementSuggestion suggestion, int min,
                                      int max) {
        // [rule] force wheat groups to be min maxed
        if (suggestion.info.type == TileType.Wheat) {
          if (suggestion.info.associated_group.tiles.size() > max ||
              suggestion.info.associated_group.tiles.size() < min) {
            if (PRINT_DEBUG)
              System.out.println("[rule] force wheat groups to be min maxed");
            return true;
          }
        }
        return false;
      }

      boolean apply_new_wheat_group_secluded(PlacementSuggestion suggestion) {
        // [rule] force new wheat group secluded

        if (suggestion.info.type != TileType.Wheat)
          return false;

        // if (this.bot.state.wheats.contains(
        //         suggestion.info.associated_group)) // should not hit
        //   return false;

        for (var windmill : this.bot.state.windmills) {
          if (windmill.coord.distance(suggestion.coord) <= 5) {
            if (PRINT_DEBUG)
              System.out.println("[rule] force new wheat group secluded");
            return true;
          }
        }

        return false;
      }

      boolean apply_new_wheat_group(PlacementSuggestion suggestion) {
        // [rule] want create new wheat group
        if (suggestion.info.type != TileType.Wheat)
          return false;

        System.out.println("[rule] want create new wheat group");
        if (suggestion.info.associated_group.tiles.size() != 1)
          return true;

        return false;
      }

      boolean
      apply_wheat_not_to_join_other_together(PlacementSuggestion suggestion) {
        // [rule] force wheat not to join other groups
        boolean wheat_kept_separate = false;
        if (suggestion.info.type == TileType.Wheat) {
          for (var grp : this.bot.state.wheats) {
            if (grp == suggestion.info.associated_group)
              continue;

            if (grp.coords_placable.contains(suggestion.coord)) {
              wheat_kept_separate = true;
              if (PRINT_DEBUG)
                System.out.println(
                    "[rule] force wheat not to join other groups");
              break;
            }
          }
        }
        if (wheat_kept_separate)
          return true;
        return false;
      }

      boolean apply_wheat_groups_not_enclosed(PlacementSuggestion suggestion) {
        // [rule] enforce wheat groups not to be enclosed

        if (suggestion.info.type != TileType.Windmill)
          return false;

        boolean rule_wheat_groups_enclosed = false;
        for (var grp : this.bot.state.wheats) {
          if (grp.coords_placable.size() == 2 && grp.tiles.size() <= 9 &&
              !grp.is_on_border()) {
            if (suggestion.info.associated_group != grp) {
              if (grp.coords_placable.contains(suggestion.coord)) {
                rule_wheat_groups_enclosed = true;
                if (PRINT_DEBUG)
                  System.out.println(
                      "[rule] enforce wheat groups not to be enclosed");
                break;
              }
            }
          }
        }
        if (rule_wheat_groups_enclosed)
          return true;
        return false;
      }

      boolean apply_windmill_must_have_wheat(PlacementSuggestion suggestion) {
        // [rule] all windmills must have wheats
        if (suggestion.info.type != TileType.Wheat)
          return false;

        boolean rule_apply = false;
        for (var windmill : this.bot.state.windmills) {
          boolean windmill_has_wheat = false;
          for (var cring : windmill.coord.getArea(3)) {
            var ct = this.bot.map.get(cring);
            if (ct != null && ct.type == TileType.Wheat) {
              windmill_has_wheat = true;
              break;
            }
          }
          if (!windmill_has_wheat &&
              windmill.coord.distance(suggestion.coord) > 3) {
            rule_apply = true;
          }
          if (!windmill_has_wheat &&
              windmill.coord.distance(suggestion.coord) <= 3) {
            // we are fixing the imbalance...
            if (PRINT_DEBUG)
              System.out.println("[fix] all windmills must have wheats");
            return false;
          }
        }

        if (rule_apply) {
          if (PRINT_DEBUG)
            System.out.println("[rule] all windmills must have wheats");
          return true;
        }

        return false;
      }

      boolean apply_wheat_to_windmills_ratio(PlacementSuggestion suggestion) {
        // [rule] prefer adding to wheat groups until 4 tiles, if a group is
        // available
        boolean rule_wheat_group_minimum = false;
        if (suggestion.info.type == TileType.Wheat &&
            suggestion.info.associated_group != null) {
          for (var grp : this.bot.state.wheats) {
            if (suggestion.info.associated_group != grp &&
                grp.tiles.size() <= 4 && grp.addable()) {
              rule_wheat_group_minimum = true;

              if (PRINT_DEBUG)
                System.out.println(
                    "[rule] prefer adding to wheat groups until 4 tiles");
              break;
            }
          }
          if (rule_wheat_group_minimum)
            return true;
        }
        return false;
      }

      boolean apply_forest_groups_not_enclosed(PlacementSuggestion suggestion) {
        // [rule] enforce forest groups not to be enclosed
        boolean rule_forest_groups_enclosed = false;
        for (var grp : this.bot.state.forests) {
          if (grp.coords_placable.size() == 3) {
            if (suggestion.info.type != TileType.Forest) {
              if (grp.coords_placable.iterator().next().equals(
                      suggestion.coord)) {
                rule_forest_groups_enclosed = true;
                if (PRINT_DEBUG)
                  System.out.println(
                      "[rule] enforce forest groups not to be enclosed");
                break;
              }
            }
          }
        }
        if (rule_forest_groups_enclosed)
          return true;
        return false;
      }

      boolean apply_windmills_per_wheat(PlacementSuggestion suggestion) {
        // [rule] enforce windmills per wheat
        boolean rule_windmill_per_wheat = false;
        // TODO(ayham-1): fix wheats can place themselves so that they overlap,
        // breaking the rule
        if (suggestion.info.type == TileType.Windmill) {
          for (var cneighbor : suggestion.coord.getArea(3)) {
            var ct = this.bot.map.get(cneighbor);
            if (ct == null || ct.type != TileType.Wheat)
              continue;

            int windmills_per_current_wheat = 1; // suggestion
            for (var wheat_neighbor : cneighbor.getArea(3)) {
              var wct = this.bot.map.get(wheat_neighbor);
              if (wct == null || wct.type != TileType.Windmill)
                continue;
              windmills_per_current_wheat++;
            }

            if (windmills_per_current_wheat > 2) {
              rule_windmill_per_wheat = true;
              if (PRINT_DEBUG)
                System.out.println("[rule] enforce windmills per wheat");
              break;
            }
          }
        } else if (suggestion.info.type == TileType.Wheat) {
          int windmill_per_current_wheat = 0;
          for (var windmill : this.bot.state.windmills) {
            if (windmill.coord.distance(suggestion.coord) <= 3)
              windmill_per_current_wheat++;

            if (windmill_per_current_wheat > 2) {
              if (PRINT_DEBUG)
                System.out.println("[rule] enforce windmills per wheat, wheat");
              rule_windmill_per_wheat = true;
              break;
            }
          }
        }
        if (rule_windmill_per_wheat)
          return true;
        return false;
      }

      boolean
      apply_windmills_secluded_when_no_wheat(PlacementSuggestion suggestion) {
        // [rule] windmill not secluded, and has no wheats
        boolean windmill_secluded = true;
        if (suggestion.info.type != TileType.Windmill)
          return false;

        for (var cring : suggestion.coord.getArea(3)) {
          var ct = this.bot.map.get(cring);
          if (ct != null && ct.type == TileType.Wheat)
            return false;
        }

        boolean has_neighbor = false;
        for (var cring : suggestion.coord.getArea(1)) {
          var ct = this.bot.map.get(cring);
          if (ct != null && !has_neighbor)
            has_neighbor = true;
          else if (ct != null && has_neighbor) {
            windmill_secluded = false;
            break;
          }
        }

        if (windmill_secluded)
          return false;

        return true;
      }

      boolean apply_windmills_groupings(PlacementSuggestion suggestion) {
        // [rule] enforce windmills groupings
        boolean rule_windmill_groupings = false;
        if (suggestion.info.type == TileType.Windmill) {
          int windmills_closeby = 0;
          for (var other : this.bot.state.windmills) {
            if (other.coord.distance(suggestion.coord) <= 3)
              windmills_closeby++;
          }
          if (windmills_closeby > 2) {
            rule_windmill_groupings = true;
            if (PRINT_DEBUG)
              System.out.println("[rule] enforce windmills groupings");
          }
          if (rule_windmill_groupings)
            return true;
        }
        return false;
      }

      boolean apply_wheats_near_windmills_when_possible(
          PlacementSuggestion suggestion) {
        // [rule] wheats near windmills when possible always
        boolean rule_wheats_near_windmills_always = true;
        if (suggestion.info.type == TileType.Wheat &&
            !this.bot.state.windmills.isEmpty()) {
          boolean is_near = false;
          for (var windmill : this.bot.state.windmills) {
            if (((WindmillRepr)windmill).has_space() &&
                suggestion.coord.distance(windmill.coord) <= 3) {
              is_near = true;
              break;
            }
          }
          if (is_near)
            rule_wheats_near_windmills_always = false;

          if (rule_wheats_near_windmills_always) {
            if (PRINT_DEBUG)
              System.out.println("[rule] wheats near windmills always");
            return true;
          }
        }
        return false;
      }

      boolean
      apply_windmills_not_beside_forest(PlacementSuggestion suggestion) {
        // [rule] windmills not beside forest
        boolean rule_windmill_beside_forest = false;
        if (suggestion.info.type == TileType.Windmill) {
          for (var cneighbor : suggestion.coord.getRing(1)) {
            var ct = this.bot.map.get(cneighbor);
            if (ct == null || ct.type != TileType.Forest)
              continue;
            rule_windmill_beside_forest = true;
            if (PRINT_DEBUG)
              System.out.println("[rule] windmills not beside forest");
            break;
          }
        } else if (suggestion.info.type == TileType.Forest) {
          for (var cneighbor : suggestion.coord.getRing(1)) {
            var ct = this.bot.map.get(cneighbor);
            if (ct == null || ct.type != TileType.Windmill)
              continue;
            rule_windmill_beside_forest = true;
            if (PRINT_DEBUG)
              System.out.println("[rule] forests not beside windmill");
            break;
          }
        }
        if (rule_windmill_beside_forest)
          return true;
        return false;
      }

      boolean apply_avoid_windmills(PlacementSuggestion suggestion) {
        // [rule] avoid windmills when placing stuff other than wheats
        boolean rule_windmill_avoid_non_wheat = false;
        if (suggestion.info.type != TileType.Windmill &&
            suggestion.info.type != TileType.Wheat &&
            suggestion.info.type != TileType.Beehive) {
          for (var windmill : this.bot.state.windmills) {
            for (var cneighbor : windmill.coord.getArea(3)) {
              if (suggestion.coord.equals(cneighbor)) {
                rule_windmill_avoid_non_wheat = true;
                if (PRINT_DEBUG)
                  System.out.println("[rule] avoid windmills when placing "
                                     + "stuff other than wheats");
                break;
              }
            }
            if (rule_windmill_avoid_non_wheat)
              break;
          }
          if (rule_windmill_avoid_non_wheat)
            return true;
        }
        return false;
      }

      boolean
      apply_beehives_not_beside_windmills(PlacementSuggestion suggestion) {
        // [rule] beehives not beside windmills
        boolean rule_beehives_beside_windmills = false;
        if (suggestion.info.type == TileType.Beehive) {
          for (var repr : this.bot.state.windmills) {
            if (repr.coord.distance(suggestion.coord) <= 3) {
              rule_beehives_beside_windmills = true;
              break;
            }
          }
        }
        if (rule_beehives_beside_windmills)
          return true;
        return false;
      }

      boolean apply_dhouse_less_than_3(PlacementSuggestion suggestion) {
        // [rule] doublehouses don't have more than 3
        boolean rule_dhouses_neighbors = false;
        if (suggestion.info.type == TileType.DoubleHouse ||
            suggestion.info.type == TileType.SmallHouse) {
          for (var repr : this.bot.state.dhouses) {
            int counter = 0;
            for (var cring : repr.coord.getRing(1)) {
              var ct = this.bot.map.get(cring);
              if (ct == null)
                continue;
              if (ct.type == TileType.SmallHouse ||
                  ct.type == TileType.DoubleHouse)
                counter++;
            }
            if (suggestion.coord.distance(repr.coord) <= 1)
              counter++;
            if (counter > 3) {
              rule_dhouses_neighbors = true;

              if (PRINT_DEBUG)
                System.out.println(
                    "[rule] doublehouses don't have more than 3");
              break;
            }
          }
        }
        if (rule_dhouses_neighbors)
          return true;
        return false;
      }

      boolean apply_moais_spaced(PlacementSuggestion suggestion) {
        // [rule] moais spaced
        boolean rule_moai_close_spaced = false;
        if (suggestion.info.type == TileType.Moai) {
          for (var moai : this.bot.state.moais) {
            if (moai.coord.distance(suggestion.coord) <= 4) {
              rule_moai_close_spaced = true;
              if (PRINT_DEBUG)
                System.out.println("[rule] moais spaced");
              break;
            }
          }
          if (rule_moai_close_spaced)
            return true;
        }
        return false;
      }

      boolean apply_stones_not_beside_wheat(PlacementSuggestion suggestion) {
        // [rule] stones not beside wheat
        boolean rule_stones_beside_wheat = false;
        if (suggestion.info.type == TileType.StoneHill ||
            suggestion.info.type == TileType.StoneRocks ||
            suggestion.info.type == TileType.StoneMountain) {
          for (var cring : suggestion.coord.getRing(1)) {
            var ct = this.bot.map.get(cring);
            if (ct == null)
              continue;

            if (ct.type == TileType.Wheat &&
                !(ct.associated_group.tiles.size() <= 1 &&
                  this.bot.state.wheats.size() <= 1)) {
              rule_stones_beside_wheat = true;
              if (PRINT_DEBUG)
                System.out.println("[rule] stones not beside wheat");
              break;
            }
          }
        }
        if (rule_stones_beside_wheat)
          return true;
        return false;
      }

      boolean apply_stones_not_beside_windmill(PlacementSuggestion suggestion) {
        // [rule] stones not beside windmill
        boolean rule_stones_beside_windmill = false;
        if (suggestion.info.type == TileType.StoneHill ||
            suggestion.info.type == TileType.StoneRocks ||
            suggestion.info.type == TileType.StoneMountain ||
            suggestion.info.type == TileType.StoneQuarry) {
          for (var windmill : this.bot.state.windmills) {
            if (windmill.coord.distance(suggestion.coord) <= 3) {
              rule_stones_beside_windmill = true;
              if (PRINT_DEBUG)
                System.out.println("[rule] stones not beside windmill");
              break;
            }
          }
        }
        if (rule_stones_beside_windmill)
          return true;
        return false;
      }

      boolean apply_shouses_grouped(PlacementSuggestion suggestion) {
        // [rule] small houses are grouped together when possible
        boolean rule_shouses_grouped =
            (this.bot.state.shouses.isEmpty()) ? false : true;
        if (suggestion.info.type == TileType.SmallHouse) {
          for (var cring : suggestion.coord.getRing(1)) {
            var ct = this.bot.map.get(cring);
            if (ct == null)
              continue;
            if (ct.type == TileType.DoubleHouse)
              return false;

            if (ct.type == TileType.SmallHouse) {
              rule_shouses_grouped = false;
              break;
            }
          }
          if (rule_shouses_grouped) {
            if (PRINT_DEBUG)
              System.out.println(
                  "[rule] small houses are grouped together when possible");
            return true;
          }
        }
        return false;
      }
    }
  }

  class GreedyDictator extends Director {
    /* Greedy dictator, just selects the placement with highest score delta,
     * no consideration for the future, rules with no rules */

    GreedyDictator(MyBot bot) { super(bot); }

    @Override
    PlacementSuggestion pick() {
      Collections.sort(this.suggestions);
      if (!this.suggestions.isEmpty())
        return this.suggestions.getLast();
      return null;
    }

    @Override
    int redraw_max_times() {
      return this.redraw_max_times(0.15f, 5.0f, 0.05f);
    }

    @Override
    boolean do_redraw() {
      return this.do_redraw(3.0f / 5.0f, 0.0f);
    }
  }

  class StrictDictator extends Director {
    /* strict dictator, has the most intended rules, does not fain from
     * rejecting suggestions or placements, rules are to be followed,
     * does not care about anything other than the rules.
     * Rules:
     *  read the code you lazy
     * */

    StrictDictator(MyBot bot) { super(bot); }

    @Override
    int redraw_max_times() {
      return this.redraw_max_times(0.25f, 5.0f, 0.00f);
    }

    @Override
    boolean do_redraw() {
      return this.do_redraw(3.0f / 5.0f, 0.0f);
    }

    @Override
    PlacementSuggestion pick() {
      PlacementSuggestion best = null;

      Collections.sort(this.suggestions);

      for (var suggestion : this.suggestions.reversed()) {
        if (this.rules.apply_wheat_groups_size(suggestion, 1, 9))
          continue;

        if (this.rules.apply_wheat_not_to_join_other_together(suggestion))
          continue;

        if (this.rules.apply_wheat_groups_not_enclosed(suggestion))
          continue;

        if (this.rules.apply_windmills_per_wheat(suggestion))
          continue;

        if (this.rules.apply_windmills_groupings(suggestion))
          continue;

        if (this.rules.apply_wheats_near_windmills_when_possible(suggestion))
          continue;

        if (this.rules.apply_windmills_not_beside_forest(suggestion))
          continue;

        if (this.rules.apply_dhouse_less_than_3(suggestion))
          continue;

        if (this.rules.apply_moais_spaced(suggestion))
          continue;

        if (this.rules.apply_stones_not_beside_wheat(suggestion))
          continue;

        if (this.rules.apply_stones_not_beside_windmill(suggestion))
          continue;

        best = suggestion;
        break;
      }

      return best;
    }
  }

  class DynamicDictator extends Director {
    /* dynamic dictator, follows rules, but doesn't worship them,
     * sometimes rules are meant to be broken, and this dictator
     * calculates when to break them. sometimes rules are outdated
     * mid-game, or sometimes rules are meant for late-game
     *
     * Rules:
     *  read the code you lazy
     * */

    DynamicDictator(MyBot bot) { super(bot); }

    @Override
    int redraw_max_times() {
      return this.redraw_max_times(0.16f, 6.0f, 0.00f);
    }

    @Override
    boolean do_redraw() {
      return this.do_redraw(3.0f / 5.0f, 0.0f);
    }

    // state
    boolean new_wheat_group_forced_secluded = false;
    int start_new_wheat_group_for_windmills = 2;

    @Override
    PlacementSuggestion pick() {
      PlacementSuggestion best = null;

      Collections.sort(this.suggestions);
      if (PRINT_DEBUG)
        System.out.println("[round] " + this.bot.round);

      for (var suggestion : this.suggestions.reversed()) {
        boolean is_wheat_beside_windmill = false;
        if (suggestion.info.type == TileType.Wheat) {
          for (var windmill : this.bot.state.windmills) {
            if (windmill.coord.distance(suggestion.coord) <= 3) {
              is_wheat_beside_windmill = true;
              break;
            }
          }
        }

        // if (this.bot.round <= 1 &&
        if (this.rules.apply_wheat_groups_size(suggestion, 0, 9))
          continue;

        if (this.rules.apply_windmill_must_have_wheat(suggestion) &&
            this.bot.round >= 7)
          continue;

        // if (/*!(is_wheat_beside_windmill && this.bot.round >= 4) &&*/
        // if (this.bot.round >= 5 &&
        //    this.rules.apply_wheat_groups_size(suggestion, 0, 5)) {
        //  new_wheat_group_forced_secluded = true;
        //  continue;
        //}

        // if (this.bot.round >= 8 &&
        //     this.rules.apply_wheat_groups_size(suggestion, 0, 9))
        //   continue;

        if (!(is_wheat_beside_windmill && this.bot.round >= 8) &&
            this.rules.apply_wheat_not_to_join_other_together(suggestion))
          continue;

        if (this.rules.apply_wheat_groups_not_enclosed(suggestion))
          continue;

        if (this.rules.apply_windmills_per_wheat(suggestion))
          continue;

        if (this.rules.apply_windmills_groupings(suggestion))
          continue;

        if (this.rules.apply_avoid_windmills(suggestion) && this.bot.round <= 4)
          continue;

        if (this.rules.apply_wheats_near_windmills_when_possible(suggestion))
          continue;

        if (this.rules.apply_windmills_not_beside_forest(suggestion))
          continue;

        if (this.rules.apply_beehives_not_beside_windmills(suggestion) &&
            this.bot.round >= 7)
          continue;

        if (this.rules.apply_dhouse_less_than_3(suggestion))
          continue;

        if (this.rules.apply_moais_spaced(suggestion))
          continue;

        if (this.rules.apply_stones_not_beside_wheat(suggestion) /*&&
            this.bot.round <= 3*/)
          continue;

        if (this.rules.apply_stones_not_beside_windmill(
                suggestion) /*&& this.bot.round <= 6*/)
          continue;

        best = suggestion;
        break;
      }

      return best;
    }
  }

  // Director director = new GreedyDictator(this);
  // Director director = new StrictDictator(this);
  Director director = new DynamicDictator(this);

  // API vars
  World world;
  Controller controller;

  //// state vars
  int round = 0;
  int redraw_counter = 0;
  boolean is_first = true;
  boolean must_win = false;

  // track the map ourselves
  Map<CubeCoordinate, TileInfo> map = new LinkedHashMap<>();

  /* placable coords that are not-isolated, manually updated every placed
   * tile and every round change */
  Set<CubeCoordinate> coords_placable = new LinkedHashSet<>();
  Set<CubeCoordinate> coords_placable_potential = new LinkedHashSet<>();

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
      this.director.redraw_counter = 0;
      if (!this.reachable_money || !this.reachable_food ||
          !this.reachable_materials)
        this.must_win = true;
      else {
        this.must_win = false;
      }
      this.update_coords_placable();

      // TODO(ayham-1): maybe this is not needed could be better
      this.director.new_card();
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
    if (!this.coords_placable.isEmpty() && world.getRedrawTime() <= 0) {
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
      boolean all_configured = true;
      for (var market : this.state.marketplaces)
        if (((MarketplaceRepr)market).configured == false)
          all_configured = false;

      if (all_configured)
        for (var market : this.state.marketplaces)
          ((MarketplaceRepr)market).configured = false;
    }

    if (!this.controller.actionPossible())
      return;

    // redraw when we are winning!!1
    if (this.director.do_redraw())
      this.controller.redraw();

    if (!this.controller.actionPossible())
      return;

    // make world zero as first tile
    if (is_first) {
      this.coords_placable.add(new CubeCoordinate());
      this.coords_placable_potential.add(new CubeCoordinate());
      is_first = false;
    }

    if (!this.world.getHand().isEmpty() && !this.coords_placable.isEmpty()) {
      List<TileType> hand = this.world.getHand().hand;
      PlacementSuggestion suggestion = null;

      while (suggestion == null && !hand.isEmpty()) {
        this.director.new_card();
        TileType card = this.director.get_preferred_tile(hand);
        this.director.ask_for_suggestions(card);
        suggestion = this.director.pick();
        hand.remove(card);
      }
      if (suggestion != null)
        this.director.accept_suggestion(suggestion);
    }

    // only update marketplaces if actions are leftover, should be in the
    // following situations:
    // - no tiles placed because hand is empty
    // - or no tiles placed, because director rejected to place them right now
    if (this.controller.actionPossible())
      this.setup_marketplaces();
  }

  boolean place_tile(CubeCoordinate coord, TileInfo info) {
    if (PRINT_DEBUG) {
      System.out.println("[place] want " + info.type + " on " + coord);
      System.out.println("[place] grp " + info.associated_group +
                         (info.associated_group != null
                              ? " sz: " + info.associated_group.tiles.size()
                              : ""));
      System.out.println("[place] repr " + info.associated_repr);
    }

    if (coord == null)
      return false;
    if (PRINT_DEBUG)
      System.out.println("[place] is_coord_usable: " +
                         (this.world.getMap().at(coord) == null));
    if (world.getMap().at(coord) != null)
      return false;

    if (!world.getBuildArea().contains(coord))
      return false;

    try {
      controller.placeTile(info.type, coord); // the heart of it all...
      this.map.put(coord, info);
    } catch (Exception e) {
      if (PRINT_DEBUG)
        System.out.println("SOMETHING VERY BAD HAPPENED HELP HELP: " +
                           e.toString());
    } finally {
      // world does not actually update when calling controller.placeTile() in
      // the same turn
      for (var cring : coord.getRing(1)) {
        if (world.getMap().at(cring) == null &&
            world.getBuildArea().contains(cring))
          this.coords_placable.add(cring);
        this.coords_placable_potential.add(cring);
      }

      this.coords_placable.remove(coord);
      this.coords_placable_potential.remove(coord);

      for (var wheat_group : this.state.wheats)
        wheat_group.update_coords_placable(coord);
      for (var forest_group : this.state.forests)
        forest_group.update_coords_placable(coord);

      if (this.coords_placable.isEmpty()) {
        for (var c : new CubeCoordinate().getRing(
                 this.world.getBuildArea().getRadius() + 1))
          this.coords_placable_potential.add(c);
      }
    }
    return true;
  }

  // TODO(ayham-1): track moai tiles separately for quick access
  double get_best_boost(CubeCoordinate coord) {
    // TODO(ayham-1): put this in grouping
    double boost = 0.0f;
    for (var cring : coord.getArea(3)) {
      var crt = this.map.get(cring);
      if (crt == null)
        continue;

      double current_boost = 0.0f;
      if (crt.type == TileType.Moai) {
        Set<TileType> unique = new LinkedHashSet<>();
        for (var cneighbor : cring.getArea(4)) {
          var un = this.map.get(cneighbor);
          if (un != null)
            unique.add(un.type);
        }
        current_boost = 1 + 0.5f * Math.max(0, unique.size() - 2);
        if (current_boost >= boost)
          boost = current_boost;
      }
    }

    return boost;
  }

  void update_coords_placable() {
    for (var c : this.world.getBuildArea()) {
      if (world.getMap().at(c) != null)
        continue;
      if (world.getMap().getNeighbors(c).hasNext() == true)
        this.coords_placable.add(c);
    }

    for (var wheat_group : this.state.wheats) {
      wheat_group.update_coords_placable();
      this.state.managed_placable_coords.addAll(wheat_group.coords_placable);
    }
    for (var forest_group : this.state.forests) {
      forest_group.update_coords_placable();
      this.state.managed_placable_coords.addAll(forest_group.coords_placable);
    }
  }

  double market_get_food_rate() {
    double perc_food = 0.0f;
    if (this.resource_current.money >= this.resource_current.food)
      return perc_food;
    double ratio = (this.resource_growth.food / this.resource_growth.money);
    perc_food = Math.log10(ratio * 5.0f);
    return Math.clamp(perc_food, 0.0f, 1.0f);
  }

  double market_get_materials_rate() {
    double perc_mat = 0.0f;
    if (this.resource_current.money >= this.resource_current.materials)
      return perc_mat;
    double ratio =
        (this.resource_current.materials / this.resource_current.money);
    // perc_mat = Math.log10(ratio) * 2.5f;
    perc_mat = (this.resource_current.materials - this.resource_current.money) /
               this.resource_current.materials;
    return Math.clamp(perc_mat, 0.0f, 1.0f);
  }

  void setup_marketplaces() {
    for (var repr : this.state.marketplaces) {
      MarketplaceRepr market = (MarketplaceRepr)repr;
      if (market.configured == false) {
        // double perc_food = this.market_get_food_rate();
        double perc_food = 0.0f; // this shit ain't balanced
        double perc_mat = this.market_get_materials_rate();

        this.controller.configureMarket(market.coord, perc_food, perc_mat);
        market.perc_food = perc_food;
        market.perc_mat = perc_mat;
        market.configured = true;
        if (!this.controller.actionPossible())
          return;
      }
    }
  }

  void group_count_set(TileType type, CubeCoordinate start,
                       Set<CubeCoordinate> coords) {

    if (this.map.get(start) == null)
      return;

    if (this.map.get(start).type == type) {
      coords.add(start);
    }

    for (var cneighbor : start.getRing(1)) {
      var tneighbor = this.map.get(cneighbor);
      if (tneighbor == null)
        continue;

      if (tneighbor.type == type) {
        if (!coords.contains(cneighbor)) {
          coords.add(cneighbor);
          group_count_set(type, cneighbor, coords);
        }
      }
    }
  }

  int group_count(TileType type, CubeCoordinate start) {
    Set<CubeCoordinate> coord = new LinkedHashSet<CubeCoordinate>();
    group_count_set(type, start, coord);

    return coord.size();
  }

  ResourceType get_resource_type(TileType type) {
    if (type == TileType.Wheat || type == TileType.Beehive ||
        type == TileType.Windmill)
      return ResourceType.Food;
    else if (type == TileType.StoneHill || type == TileType.StoneRocks ||
             type == TileType.StoneMountain || type == TileType.StoneQuarry)
      return ResourceType.Materials;
    else
      return ResourceType.Money;
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

    if (this.world.getHand().isEmpty() && event_empty_hand_start)
      this.resource_growth_last = this.resource_growth;
  }
}
