import java.util.ArrayList;
import java.util.Random;

//this is a grouping of methods to randomly generate any given game object.
//A room, an interactable, a floor, an enemy, etc.
public class Generator {
    //TODO: how do I generate a random interactable?
    public static Interactable generateInteractable(){
        Random rand = new Random();
        Interactable result;
        switch (rand.nextInt(1)) {
            case 0:
                result = chest();
                break;
            case 1:
                result = knife();
                break;
            case 2:
                result = shortSword();
                break;
            case 3:
                result = sword();
                break;
            case 4:
                result = greatSword();
                break;
            case 5:
                result = rapier();
                break;
            default:
                result = null;
                break;
        }


        return result;
    }

    //interactables
    private static Interactable chest(){
        Random rand = new Random();
        ArrayList<Interactable> chestInventory = new ArrayList<Interactable>();

        for(int i = 0;i<rand.nextInt(3);i++){
            chestInventory.add(generateInteractable());
        }

        Interactable result = new Interactable(5,20,false,"Chest","It's a box.",chestInventory);
        return result;
    }

    //Weapons
    private static Weapon knife(){
        Weapon result = new Weapon(1, 5, true,"Knife","It's kinda sharp.");
        return result;
    }

    private static Weapon shortSword(){
        Weapon result = new Weapon(2, 8, true,"Short Sword","Like a knife, but long.");
        return result;
    }

    private static Weapon sword(){
        Weapon result = new Weapon(2, 9, true,"Sword","It might hurt someone");
        return result;
    }

    private static Weapon greatSword(){
        Weapon result = new Weapon(3,11,true,"Greatsword","This is a big sword.");
        return result;
    }

    private static Weapon rapier(){
        Weapon result = new Weapon(1, 6, true,"Rapier","Thin and deadly.");
        return result;
    }

}
