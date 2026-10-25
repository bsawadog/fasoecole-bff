package org.afritechinnovations.service.academic;
import java.util.List;

/** Editable starter catalogs, not prescribed curricula or class coefficients. */
public final class SubjectCatalog {
 private SubjectCatalog(){}
 public record Entry(String code,String name,String category){}
 public record Pack(String id,String label,List<Entry> subjects){}
 private static Entry e(String code,String name,String category){return new Entry(code,name,category);}
 public static final List<Pack> PACKS=List.of(
  new Pack("SECONDARY","Secondaire général",List.of(
   e("FRA","Français","Littéraire"),e("MAT","Mathématiques","Scientifique"),e("ANG","Anglais","Langue"),e("ALL","Allemand","Langue"),e("ESP","Espagnol","Langue"),
   e("HGE","Histoire-Géographie","Sciences humaines"),e("PHI","Philosophie","Littéraire"),e("SVT","Sciences de la vie et de la Terre","Scientifique"),e("PCH","Physique-Chimie","Scientifique"),
   e("ECM","Éducation civique et morale","Civique"),e("EPS","Éducation physique et sportive","Sport"),e("INF","Informatique","Technologie"),e("SES","Sciences économiques et sociales","Économie"),e("APL","Arts plastiques","Arts"),e("MUS","Musique","Arts"))),
  new Pack("BUSINESS","Comptabilité, gestion et commerce",List.of(
   e("CPT","Comptabilité générale","Gestion et commerce"),e("CAN","Comptabilité analytique","Gestion et commerce"),e("CGE","Comptabilité de gestion","Gestion et commerce"),e("ECO","Économie générale","Économie"),e("DRT","Droit","Droit"),e("FIS","Fiscalité","Gestion et commerce"),e("GFI","Gestion financière","Gestion et commerce"),e("MGT","Management","Gestion et commerce"),e("COM","Commerce","Gestion et commerce"),e("VTE","Techniques de vente","Gestion et commerce"),e("MER","Mercatique","Gestion et commerce"),e("STA","Statistiques","Scientifique"),e("BUR","Bureautique","Technologie"),e("SEC","Secrétariat","Gestion et commerce"))),
  new Pack("COMPUTING","Informatique et technologies numériques",List.of(
   e("ALG","Algorithmique","Informatique"),e("PRG","Programmation","Informatique"),e("BDD","Bases de données","Informatique"),e("RES","Réseaux informatiques","Informatique"),e("SYS","Systèmes d’exploitation","Informatique"),e("WEB","Développement web","Informatique"),e("MNT","Maintenance informatique","Informatique"),e("ARC","Architecture des ordinateurs","Informatique"),e("CYB","Cybersécurité","Informatique"))),
  new Pack("ELECTRICAL","Électricité et électronique",List.of(
   e("ELC","Électricité générale","Électricité et électronique"),e("ELM","Électrotechnique","Électricité et électronique"),e("ELN","Électronique","Électricité et électronique"),e("AUT","Automatisme","Électricité et électronique"),e("INS","Installations électriques","Électricité et électronique"),e("MES","Mesures électriques","Électricité et électronique"),e("MEE","Maintenance électrique","Électricité et électronique"),e("DES","Dessin technique","Électricité et électronique"),e("TPR","Travaux pratiques","Électricité et électronique"))),
  new Pack("MECHANICAL","Mécanique et maintenance industrielle",List.of(
   e("MEC","Mécanique générale","Mécanique et industrie"),e("FAB","Fabrication mécanique","Mécanique et industrie"),e("DTI","Dessin industriel","Mécanique et industrie"),e("RDM","Résistance des matériaux","Sciences de l’ingénieur"),e("HYD","Hydraulique","Mécanique et industrie"),e("PNE","Pneumatique","Mécanique et industrie"),e("MMI","Maintenance industrielle","Mécanique et industrie"),e("USI","Usinage","Mécanique et industrie"),e("THM","Thermodynamique","Mécanique et industrie"))),
  new Pack("BUILDING","Génie civil et bâtiment",List.of(
   e("BTP","Technologie du bâtiment","Génie civil et bâtiment"),e("TOP","Topographie","Génie civil et bâtiment"),e("RDM","Résistance des matériaux","Sciences de l’ingénieur"),e("BET","Béton armé","Génie civil et bâtiment"),e("MET","Métré et étude des prix","Génie civil et bâtiment"),e("DAO","Dessin assisté par ordinateur","Génie civil et bâtiment"),e("MATC","Matériaux de construction","Génie civil et bâtiment"),e("CON","Construction","Génie civil et bâtiment"),e("SCT","Sécurité de chantier","Génie civil et bâtiment")))
 );
 public static Pack pack(String id){return PACKS.stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Catalogue de matières inconnu."));}
 public static String normalize(String value){return value==null?"":java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
}
