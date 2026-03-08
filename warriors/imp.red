; name Imp
; Strategie : se copie dans la case suivante a chaque cycle
; Il avance dans la memoire comme un virus
; Tres difficile a tuer car il se propage partout

MOV 0, 1
; MOV 0,1 = copie l'instruction a offset 0 (moi-meme)
;           vers l'instruction a offset 1 (la case suivante)
; Resultat : je me duplique en avancant de 1 case a chaque cycle