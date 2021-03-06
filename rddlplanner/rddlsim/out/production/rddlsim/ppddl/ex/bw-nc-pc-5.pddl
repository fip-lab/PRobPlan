;; Generated by blocksworld generator
;; http://www.cs.rutgers.edu/~jasmuth/blocksworld.tar.gz
;; by John Asmuth (jasmuth@cs.rutgers.edu)
;; and Dave Weissman

(define (domain bw-nc-pc-5)
 (:requirements :adl :probabilistic-effects :fluents :rewards)
 (:types block table)
 (:constants table - table)
 (:predicates
  (holding ?block - block)
  (on-top-of ?block - block ?obj)
 )
 (:action pick-up-block-from
  :parameters (?top - block ?bottom)
  :precondition (and (not (= ?top ?bottom))
                     (forall (?b - block) (not (holding ?b)))
                     (on-top-of ?top ?bottom)
                     (forall (?b - block) (not (on-top-of ?b ?top))))
  :effect                 (and (decrease (reward) 1)
               (probabilistic 0.75 (and (holding ?top)
                                        (not (on-top-of ?top ?bottom)))
                              0.25 (when (not (= ?bottom table))
                                         (and (not (on-top-of ?top ?bottom))
                                              (on-top-of ?top table)))))
 )
 (:action put-down-block-on
  :parameters (?top - block ?bottom)
  :precondition (and (not (= ?top ?bottom))
                     (holding ?top)
                     (or (= ?bottom table)
                         (forall (?b - block) (not (on-top-of ?b ?bottom)))))
  :effect                 (and (not (holding ?top))
               (probabilistic 0.75 (on-top-of ?top ?bottom)
                              0.25 (on-top-of ?top table)))
 )
)
(define (problem bw-nc-pc-5)
 (:domain bw-nc-pc-5)
 (:objects block0 block1 block2 block3 block4 - block)
 (:init
  (on-top-of block0 table)
  (on-top-of block1 table)
  (on-top-of block2 block3)
  (on-top-of block3 table)
  (on-top-of block4 table)
 )
 (:goal
  (and
   (on-top-of block0 table)
   (on-top-of block3 block2)
   (on-top-of block2 block1)
   (on-top-of block1 block4)
   (on-top-of block4 table)

  )
 )
 (:goal-reward 500)
)
